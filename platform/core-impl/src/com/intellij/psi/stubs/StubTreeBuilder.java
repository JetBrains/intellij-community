// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.diagnostic.PluginException;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.TreeBackedLighterAST;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.roots.impl.PushedFilePropertiesRetriever;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.IndexedFile;
import com.intellij.util.indexing.IndexingDataKeys;
import com.intellij.util.indexing.PsiDependentFileContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;

public final class StubTreeBuilder {

  private static final Logger LOG = Logger.getInstance(StubTreeBuilder.class);

  private static final Key<Stub> stubElementKey = Key.create("stub.tree.for.file.content");

  private StubTreeBuilder() { }

  static boolean requiresContentToFindBuilder(@NotNull FileType fileType) {
    BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
    return builder instanceof BinaryFileStubBuilder.CompositeBinaryFileStubBuilder<?>;
  }

  public static StubBuilderType getStubBuilderType(@NotNull IndexedFile file, boolean toBuild) {
    FileType fileType = file.getFileType();
    BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
    if (builder != null) {
      if (builder instanceof BinaryFileStubBuilder.CompositeBinaryFileStubBuilder<?>) {
        Object subBuilder = ((BinaryFileStubBuilder.CompositeBinaryFileStubBuilder<?>)builder).getSubBuilder((FileContent) file);
        return new StubBuilderType((BinaryFileStubBuilder.CompositeBinaryFileStubBuilder<?>)builder, subBuilder);
      } else {
        return new StubBuilderType(builder);
      }
    }

    if (fileType instanceof LanguageFileType) {
      Language l = ((LanguageFileType)fileType).getLanguage();
      ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(l);
      if (parserDefinition == null) {
        return null;
      }

      IFileElementType elementType = parserDefinition.getFileNodeType();
      if (!(elementType instanceof IStubFileElementType)) return null;
      VirtualFile vFile = file.getFile();
      boolean shouldBuildStubFor = ((IStubFileElementType<?>)elementType).shouldBuildStubFor(vFile);
      if (toBuild && !shouldBuildStubFor) return null;
      PushedFilePropertiesRetriever pushedFilePropertiesRetriever = PushedFilePropertiesRetriever.getInstance();
      @NotNull List<String> properties = pushedFilePropertiesRetriever != null
                                         ? pushedFilePropertiesRetriever.dumpSortedPushedProperties(vFile)
                                         : Collections.emptyList();
      return new StubBuilderType((IStubFileElementType<?>)elementType,  properties);
    }

    return null;
  }

  @Nullable
  public static Stub buildStubTree(@NotNull FileContent inputData) {
    StubBuilderType type = getStubBuilderType(inputData, false);
    if (type == null) return null;
    return buildStubTree(inputData, type);
  }

  private static <T> @Nullable T handleStubBuilderException(@NotNull FileContent inputData,
                                                            @NotNull StubBuilderType stubBuilderType,
                                                            @NotNull ThrowableComputable<T, Exception> builder) {
    try {
      return builder.compute();
    }
    catch (CancellationException ce) {
      throw ce;
    }
    catch (Exception e) {
      if (e instanceof ControlFlowException) ExceptionUtil.rethrowUnchecked(e);
      LOG.error(PluginException.createByClass("Failed to build stub tree for " + inputData.getFileName(), e,
                                              stubBuilderType.getClassToBlameInCaseOfException()));
      return null;
    }
  }

  @Nullable
  public static Stub buildStubTree(@NotNull FileContent inputData, @NotNull StubBuilderType stubBuilderType) {
    Stub data = inputData.getUserData(stubElementKey);
    if (data != null) return data;

    synchronized (inputData) {
      data = inputData.getUserData(stubElementKey);
      if (data != null) return data;

      BinaryFileStubBuilder builder = stubBuilderType.getBinaryFileStubBuilder();
      if (builder != null) {
        data = handleStubBuilderException(inputData, stubBuilderType, () -> builder.buildStubTree(inputData));
        if (data instanceof PsiFileStubImpl && !((PsiFileStubImpl<?>)data).rootsAreSet()) {
          ((PsiFileStubImpl<?>)data).setStubRoots(new PsiFileStub[]{(PsiFileStubImpl<?>)data});
        }
      }
      else {
        CharSequence contentAsText = inputData.getContentAsText();
        PsiDependentFileContent fileContent = (PsiDependentFileContent)inputData;
        FileViewProvider viewProvider = fileContent.getPsiFile().getViewProvider();
        PsiFile psi = viewProvider.getStubBindingRoot();
        // if we load AST, it should be easily gc-able. See PsiFileImpl.createTreeElementPointer()
        data = psi.getManager().runInBatchFilesMode(() -> {
          psi.putUserData(IndexingDataKeys.FILE_TEXT_CONTENT_KEY, contentAsText);
          StubElement built = null;
          try {
            IStubFileElementType<?> stubFileElementType = ((PsiFileImpl)psi).getElementTypeForStubBuilder();
            if (stubFileElementType != null) {
              StubBuilder stubBuilder = stubFileElementType.getBuilder();
              if (stubBuilder instanceof LightStubBuilder) {
                LightStubBuilder.FORCED_AST.set(fileContent.getLighterAST());
              }
              built = handleStubBuilderException(inputData, stubBuilderType, () -> stubBuilder.buildStubTree(psi));
              List<Pair<IStubFileElementType, PsiFile>> stubbedRoots = getStubbedRoots(viewProvider);
              List<PsiFileStub<?>> stubs = new ArrayList<>(stubbedRoots.size());
              stubs.add((PsiFileStub<?>)built);

              for (Pair<IStubFileElementType, PsiFile> stubbedRoot : stubbedRoots) {
                PsiFile secondaryPsi = stubbedRoot.second;
                if (psi == secondaryPsi) continue;
                StubBuilder stubbedRootBuilder = stubbedRoot.first.getBuilder();
                if (stubbedRootBuilder instanceof LightStubBuilder) {
                  LightStubBuilder.FORCED_AST.set(new TreeBackedLighterAST(secondaryPsi.getNode()));
                }
                StubElement<?> element = handleStubBuilderException(inputData, stubBuilderType, () -> stubbedRootBuilder.buildStubTree(secondaryPsi));
                if (element instanceof PsiFileStub) {
                  stubs.add((PsiFileStub<?>)element);
                }
                ensureNormalizedOrder(element);
              }
              PsiFileStub<?>[] stubsArray = stubs.toArray(PsiFileStub.EMPTY_ARRAY);
              for (PsiFileStub<?> stub : stubsArray) {
                if (stub instanceof PsiFileStubImpl) {
                  ((PsiFileStubImpl<?>)stub).setStubRoots(stubsArray);
                }
              }
            }
          }
          finally {
            psi.putUserData(IndexingDataKeys.FILE_TEXT_CONTENT_KEY, null);
          }
          return built;
        });
      }

      ensureNormalizedOrder(data);
      inputData.putUserData(stubElementKey, data);
      return data;
    }
  }

  private static void ensureNormalizedOrder(Stub element) {
    if (element instanceof StubBase<?>) {
      ((StubBase<?>)element).myStubList.finalizeLoadingStage();
    }
  }

  /** Order is deterministic. First element matches {@link FileViewProvider#getStubBindingRoot()} */
  @NotNull
  public static List<Pair<IStubFileElementType, PsiFile>> getStubbedRoots(@NotNull FileViewProvider viewProvider) {
    List<Trinity<Language, IStubFileElementType<?>, PsiFile>> roots =
      new SmartList<>();
    PsiFile stubBindingRoot = viewProvider.getStubBindingRoot();
    for (Language language : viewProvider.getLanguages()) {
      PsiFile file = viewProvider.getPsi(language);
      if (file instanceof PsiFileImpl) {
        IStubFileElementType<?> type = ((PsiFileImpl)file).getElementTypeForStubBuilder();
        if (type != null) {
          roots.add(Trinity.create(language, type, file));
        }
      }
    }

    ContainerUtil.sort(roots, (o1, o2) -> {
      if (o1.third == stubBindingRoot) return o2.third == stubBindingRoot ? 0 : -1;
      else if (o2.third == stubBindingRoot) return 1;
      else return StringUtil.compare(o1.first.getID(), o2.first.getID(), false);
    });

    return ContainerUtil.map(roots, trinity -> Pair.create(trinity.second, trinity.third));
  }
}