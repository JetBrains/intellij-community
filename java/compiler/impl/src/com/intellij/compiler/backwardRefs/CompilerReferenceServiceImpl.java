/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.compiler.backwardRefs;

import com.intellij.compiler.backwardRefs.BackwardReferenceIndexReaderFactory.BackwardReferenceReader;
import com.intellij.compiler.server.CustomBuilderMessageHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import com.intellij.compiler.chainsSearch.ChainSearchMagicConstants;
import com.intellij.compiler.chainsSearch.MethodCall;
import com.intellij.compiler.chainsSearch.ChainOpAndOccurrences;
import com.intellij.compiler.chainsSearch.TypeCast;
import com.intellij.compiler.chainsSearch.context.ChainCompletionContext;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.backwardRefs.BackwardReferenceIndexBuilder;
import org.jetbrains.jps.backwardRefs.LightRef;
import org.jetbrains.jps.backwardRefs.SignatureData;
import one.util.streamex.StreamEx;
import com.intellij.openapi.progress.ProgressManager;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CompilerReferenceServiceImpl extends CompilerReferenceServiceBase<BackwardReferenceReader>
  implements CompilerReferenceServiceEx {
  public CompilerReferenceServiceImpl(Project project,
                                      FileDocumentManager fileDocumentManager,
                                      PsiDocumentManager psiDocumentManager) {
    super(project, fileDocumentManager, psiDocumentManager, BackwardReferenceIndexReaderFactory.INSTANCE,
          (connection, compilationAffectedModules) -> connection
            .subscribe(CustomBuilderMessageHandler.TOPIC, (builderId, messageType, messageText) -> {
              if (BackwardReferenceIndexBuilder.BUILDER_ID.equals(builderId)) {
                compilationAffectedModules.add(messageText);
              }
            }));
  }

  @NotNull
  @Override
  public SortedSet<ChainOpAndOccurrences<MethodCall>> findMethodReferenceOccurrences(@NotNull String rawReturnType,
                                                                                     @SignatureData.IteratorKind byte iteratorKind,
                                                                                     @NotNull ChainCompletionContext context) {
    try {
      myReadDataLock.lock();
      try {
        if (myReader == null) throw new ReferenceIndexUnavailableException();
        final int type = myReader.getNameEnumerator().tryEnumerate(rawReturnType);
        if (type == 0) return Collections.emptySortedSet();
        return Stream.of(new SignatureData(type, iteratorKind, true), new SignatureData(type, iteratorKind, false))
          .flatMap(sd -> StreamEx.of(myReader.getMembersFor(sd))
            .peek(r -> ProgressManager.checkCanceled())
            .select(LightRef.JavaLightMethodRef.class)
            .flatMap(r -> {
              LightRef.NamedLightRef[] hierarchy =
                myReader.getHierarchy(r.getOwner(), false, false, ChainSearchMagicConstants.MAX_HIERARCHY_SIZE);
              return hierarchy == null ? Stream.empty() : Arrays.stream(hierarchy).map(c -> r.override(c.getName()));
            })
            .distinct()
            .map(r -> {
              int count = myReader.getOccurrenceCount(r);
              return count <= 1 ? null : new ChainOpAndOccurrences<>(
                new MethodCall((LightRef.JavaLightMethodRef)r, sd, context),
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

  @Nullable
  @Override
  public ChainOpAndOccurrences<TypeCast> getMostUsedTypeCast(@NotNull String operandQName)
    throws ReferenceIndexUnavailableException {
    try {
      myReadDataLock.lock();
      try {
        if (myReader == null) throw new ReferenceIndexUnavailableException();
        int nameId = getNameId(operandQName);
        if (nameId == 0) return null;
        LightRef.JavaLightClassRef target = new LightRef.JavaLightClassRef(nameId);
        OccurrenceCounter<LightRef> typeCasts = myReader.getTypeCastOperands(target, null);
        LightRef bestCast = typeCasts.getBest();
        if (bestCast == null) return null;
        return new ChainOpAndOccurrences<>(new TypeCast((LightRef.LightClassHierarchyElementDef)bestCast, target, this), typeCasts.getBestOccurrences());
      }
      finally {
        myReadDataLock.unlock();
      }
    } catch (Exception e) {
      onException(e, "best type cast search");
      return null;
    }
  }

  /**
   * finds one best candidate to do a cast type before given method call (eg.: <code>((B) a).someMethod()</code>). Follows given formula:
   *
   * #(files where method & type cast is occurred) / #(files where method is occurred) > 1 - 1 / probabilityThreshold
   */
  @Nullable
  @Override
  public LightRef.LightClassHierarchyElementDef mayCallOfTypeCast(@NotNull LightRef.JavaLightMethodRef method, int probabilityThreshold)
    throws ReferenceIndexUnavailableException {
    try {
      myReadDataLock.lock();
      try {
        if (myReader == null) throw new ReferenceIndexUnavailableException();
        final TIntHashSet ids = myReader.getAllContainingFileIds(method);

        LightRef.LightClassHierarchyElementDef owner = method.getOwner();

        OccurrenceCounter<LightRef> bestTypeCast = myReader.getTypeCastOperands(owner, ids);
        LightRef best = bestTypeCast.getBest();
        return best != null && ids.size() > probabilityThreshold * (ids.size() - bestTypeCast.getBestOccurrences())
               ? (LightRef.LightClassHierarchyElementDef)best
               : null;
      }
      finally {
        myReadDataLock.unlock();
      }
    } catch (Exception e) {
      onException(e, "conditional probability");
      return null;
    }
  }

  /**
   * conditional probability P(ref1 | ref2) = P(ref1 * ref2) / P(ref2) > 1 - 1 / threshold
   *
   * where P(ref) is a probability that ref is occurred in a file.
   */
  @Override
  public boolean mayHappen(@NotNull LightRef qualifier, @NotNull LightRef base, int probabilityThreshold) {
    try {
      myReadDataLock.lock();
      try {
        if (myReader == null) throw new ReferenceIndexUnavailableException();
        final TIntHashSet ids1 = myReader.getAllContainingFileIds(qualifier);
        final TIntHashSet ids2 = myReader.getAllContainingFileIds(base);
        final TIntHashSet intersection = intersection(ids1, ids2);

        if ((ids2.size() - intersection.size()) * probabilityThreshold < ids2.size()) {
          return true;
        }
        return false;
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

  @NotNull
  @Override
  public String getName(int idx) throws ReferenceIndexUnavailableException {
    try {
      myReadDataLock.lock();
      try {
        if (myReader == null) throw new ReferenceIndexUnavailableException();
        return myReader.getNameEnumerator().getName(idx);
      }
      finally {
        myReadDataLock.unlock();
      }
    } catch (Exception e) {
      onException(e, "find methods");
      throw new ReferenceIndexUnavailableException();
    }
  }

  @Override
  public int getNameId(@NotNull String name) throws ReferenceIndexUnavailableException {
    try {
      myReadDataLock.lock();
      try {
        if (myReader == null) throw new ReferenceIndexUnavailableException();
        int id;
        id = myReader.getNameEnumerator().tryEnumerate(name);

        return id;
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

  @NotNull
  @Override
  public LightRef.LightClassHierarchyElementDef[] getDirectInheritors(@NotNull LightRef.LightClassHierarchyElementDef baseClass) throws ReferenceIndexUnavailableException {
    try {
      myReadDataLock.lock();
      try {
        if (myReader == null) throw new ReferenceIndexUnavailableException();
        return myReader.getDirectInheritors(baseClass);
      }
      finally {
        myReadDataLock.unlock();
      }
    } catch (Exception e) {
      onException(e, "find methods");
      throw new ReferenceIndexUnavailableException();
    }
  }

  @Override
  public int getInheritorCount(@NotNull LightRef.LightClassHierarchyElementDef baseClass) throws ReferenceIndexUnavailableException {
    try {
      myReadDataLock.lock();
      try {
        if (myReader == null) throw new ReferenceIndexUnavailableException();
        LightRef.NamedLightRef[] hierarchy = myReader.getHierarchy(baseClass, false, true, -1);
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
