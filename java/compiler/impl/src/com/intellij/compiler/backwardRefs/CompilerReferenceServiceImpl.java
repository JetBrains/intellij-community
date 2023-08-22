// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs;

import com.intellij.compiler.backwardRefs.JavaBackwardReferenceIndexReaderFactory.BackwardReferenceReader;
import com.intellij.compiler.chainsSearch.ChainOpAndOccurrences;
import com.intellij.compiler.chainsSearch.ChainSearchMagicConstants;
import com.intellij.compiler.chainsSearch.MethodCall;
import com.intellij.compiler.chainsSearch.TypeCast;
import com.intellij.compiler.chainsSearch.context.ChainCompletionContext;
import com.intellij.compiler.server.BuildManagerListener;
import com.intellij.compiler.server.CustomBuilderMessageHandler;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import it.unimi.dsi.fastutil.ints.IntSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.backwardRefs.CompilerRef;
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexBuilder;
import org.jetbrains.jps.backwardRefs.SignatureData;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CompilerReferenceServiceImpl extends CompilerReferenceServiceBase<BackwardReferenceReader>
  implements CompilerReferenceServiceEx {
  public CompilerReferenceServiceImpl(Project project) {
    super(project, JavaBackwardReferenceIndexReaderFactory.INSTANCE,
          (connection, compilationAffectedModules) -> connection
            .subscribe(CustomBuilderMessageHandler.TOPIC, (builderId, messageType, messageText) -> {
              if (JavaBackwardReferenceIndexBuilder.BUILDER_ID.equals(builderId)) {
                compilationAffectedModules.add(messageText);
              }
            }));

    MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(BuildManagerListener.TOPIC, new BuildManagerListener() {
      @Override
      public void buildStarted(@NotNull Project project, @NotNull UUID sessionId, boolean isAutomake) {
        if (project == CompilerReferenceServiceImpl.this.project) {
          closeReaderIfNeeded(IndexCloseReason.COMPILATION_STARTED);
        }
      }

      @Override
      public void buildFinished(@NotNull Project project, @NotNull UUID sessionId, boolean isAutomake) {
        if (project == CompilerReferenceServiceImpl.this.project) {
          executeOnBuildThread(() -> {
            if (!ReadAction.compute(() -> CompilerReferenceServiceImpl.this.project.isDisposed())) {
              openReaderIfNeeded();
            }
          });
        }
      }
    });
  }

  @Override
  public @NotNull SortedSet<ChainOpAndOccurrences<MethodCall>> findMethodReferenceOccurrences(@NotNull String rawReturnType,
                                                                                              @SignatureData.IteratorKind byte iteratorKind,
                                                                                              @NotNull ChainCompletionContext context) {
    try {
      if (!myReadDataLock.tryLock()) {
        return Collections.emptySortedSet();
      }
      try {
        if (myReader == null) {
          throw new ReferenceIndexUnavailableException();
        }
        final int type = myReader.getNameEnumerator().tryEnumerate(rawReturnType);
        if (type == 0) {
          return Collections.emptySortedSet();
        }
        return Stream.of(new SignatureData(type, iteratorKind, true), new SignatureData(type, iteratorKind, false))
          .flatMap(sd -> StreamEx.of(myReader.getMembersFor(sd))
            .peek(r -> ProgressManager.checkCanceled())
            .select(CompilerRef.JavaCompilerMethodRef.class)
            .flatMap(r -> {
              CompilerRef.NamedCompilerRef[] hierarchy =
                myReader.getHierarchy(r.getOwner(), false, false, ChainSearchMagicConstants.MAX_HIERARCHY_SIZE);
              return hierarchy == null ? Stream.empty() : Arrays.stream(hierarchy).map(c -> r.override(c.getName()));
            })
            .distinct()
            .map(r -> {
              int count = myReader.getOccurrenceCount(r);
              return count <= 1 ? null : new ChainOpAndOccurrences<>(
                new MethodCall((CompilerRef.JavaCompilerMethodRef)r, sd, context),
                count);
            }))
          .filter(Objects::nonNull)
          .collect(Collectors.groupingBy(x -> x.getOperation(), Collectors.summarizingInt(x -> x.getOccurrenceCount())))
          .entrySet()
          .stream()
          .map(e -> new ChainOpAndOccurrences<>(e.getKey(), (int)e.getValue().getSum()))
          .collect(Collectors.toCollection(TreeSet::new));
      }
      finally {
        myReadDataLock.unlock();
      }
    }
    catch (Exception e) {
      onException(e, "find methods");
      return Collections.emptySortedSet();
    }
  }

  @Override
  public @Nullable ChainOpAndOccurrences<TypeCast> getMostUsedTypeCast(@NotNull String operandQName)
    throws ReferenceIndexUnavailableException {
    try {
      if (!myReadDataLock.tryLock()) {
        return null;
      }
      try {
        if (myReader == null) {
          throw new ReferenceIndexUnavailableException();
        }
        int nameId = getNameId(operandQName);
        if (nameId == 0) return null;
        CompilerRef.JavaCompilerClassRef target = new CompilerRef.JavaCompilerClassRef(nameId);
        OccurrenceCounter<CompilerRef> typeCasts = myReader.getTypeCastOperands(target, null);
        CompilerRef bestCast = typeCasts.getBest();
        if (bestCast == null) return null;
        return new ChainOpAndOccurrences<>(new TypeCast((CompilerRef.CompilerClassHierarchyElementDef)bestCast, target, this),
                                           typeCasts.getBestOccurrences());
      }
      finally {
        myReadDataLock.unlock();
      }
    }
    catch (Exception e) {
      onException(e, "best type cast search");
      return null;
    }
  }

  /**
   * finds one best candidate to do a cast type before given method call (eg.: <code>((B) a).someMethod()</code>). Follows given formula:
   * <p>
   * #(files where method & type cast is occurred) / #(files where method is occurred) > 1 - 1 / probabilityThreshold
   */
  @Override
  public @Nullable CompilerRef.CompilerClassHierarchyElementDef mayCallOfTypeCast(@NotNull CompilerRef.JavaCompilerMethodRef method,
                                                                                  int probabilityThreshold)
    throws ReferenceIndexUnavailableException {
    try {
      if (!myReadDataLock.tryLock()) return null;
      try {
        if (myReader == null) {
          throw new ReferenceIndexUnavailableException();
        }
        IntSet ids = myReader.getAllContainingFileIds(method);

        CompilerRef.CompilerClassHierarchyElementDef owner = method.getOwner();

        OccurrenceCounter<CompilerRef> bestTypeCast = myReader.getTypeCastOperands(owner, ids);
        CompilerRef best = bestTypeCast.getBest();
        return best != null && ids.size() > probabilityThreshold * (ids.size() - bestTypeCast.getBestOccurrences())
               ? (CompilerRef.CompilerClassHierarchyElementDef)best
               : null;
      }
      finally {
        myReadDataLock.unlock();
      }
    }
    catch (Exception e) {
      onException(e, "conditional probability");
      return null;
    }
  }

  /**
   * conditional probability P(ref1 | ref2) = P(ref1 * ref2) / P(ref2) > 1 - 1 / threshold
   * <p>
   * where P(ref) is a probability that ref is occurred in a file.
   */
  @Override
  public boolean mayHappen(@NotNull CompilerRef qualifier, @NotNull CompilerRef base, int probabilityThreshold) {
    try {
      if (!myReadDataLock.tryLock()) return false;
      try {
        if (myReader == null) {
          throw new ReferenceIndexUnavailableException();
        }
        IntSet ids1 = myReader.getAllContainingFileIds(qualifier);
        IntSet ids2 = myReader.getAllContainingFileIds(base);
        IntSet intersection = intersection(ids1, ids2);
        return (ids2.size() - intersection.size()) * probabilityThreshold < ids2.size();
      }
      finally {
        myReadDataLock.unlock();
      }
    }
    catch (Exception e) {
      onException(e, "conditional probability");
      return false;
    }
  }

  @Override
  public @NotNull String getName(int idx) throws ReferenceIndexUnavailableException {
    try {
      if (!myReadDataLock.tryLock()) {
        throw new ReferenceIndexUnavailableException();
      }
      try {
        if (myReader == null) throw new ReferenceIndexUnavailableException();
        return myReader.getNameEnumerator().valueOf(idx);
      }
      finally {
        myReadDataLock.unlock();
      }
    }
    catch (Exception e) {
      onException(e, "find methods");
      throw new ReferenceIndexUnavailableException();
    }
  }

  @Override
  public int getNameId(@NotNull String name) throws ReferenceIndexUnavailableException {
    try {
      if (!myReadDataLock.tryLock()) {
        return 0;
      }
      try {
        if (myReader == null) {
          throw new ReferenceIndexUnavailableException();
        }
        return myReader.getNameEnumerator().tryEnumerate(name);
      }
      finally {
        myReadDataLock.unlock();
      }
    }
    catch (Exception e) {
      onException(e, "get name-id");
      throw new ReferenceIndexUnavailableException();
    }
  }

  @Override
  public @NotNull Collection<CompilerRef.CompilerClassHierarchyElementDef> getDirectInheritors(@NotNull CompilerRef.CompilerClassHierarchyElementDef baseClass)
    throws ReferenceIndexUnavailableException {
    try {
      if (!myReadDataLock.tryLock()) return Collections.emptyList();
      try {
        if (myReader == null) throw new ReferenceIndexUnavailableException();

        return myReader.getDirectInheritors(baseClass);
      }
      finally {
        myReadDataLock.unlock();
      }
    }
    catch (Exception e) {
      onException(e, "find methods");
      throw new ReferenceIndexUnavailableException();
    }
  }

  @Override
  public int getInheritorCount(@NotNull CompilerRef.CompilerClassHierarchyElementDef baseClass) throws ReferenceIndexUnavailableException {
    try {
      if (!myReadDataLock.tryLock()) return -1;
      try {
        if (myReader == null) {
          throw new ReferenceIndexUnavailableException();
        }
        CompilerRef.NamedCompilerRef[] hierarchy = myReader.getHierarchy(baseClass, false, true, -1);
        return hierarchy == null ? -1 : hierarchy.length;
      }
      finally {
        myReadDataLock.unlock();
      }
    }
    catch (Exception e) {
      onException(e, "inheritor count");
      throw new ReferenceIndexUnavailableException();
    }
  }
}
