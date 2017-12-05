/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.psi.impl.source.tree.injected;

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.DocumentCommitThread;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.text.BlockSupportImpl;
import com.intellij.psi.impl.source.text.DiffLog;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.injection.ReferenceInjector;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
class InjectionRegistrarImpl extends MultiHostRegistrarImpl implements MultiHostRegistrar {
  private List<PsiFile> resultFiles;
  private List<Pair<ReferenceInjector, Place>> resultReferences;
  private Language myLanguage;
  private List<PlaceInfo> placeInfos;
  private boolean cleared = true;
  private String fileExtension;
  private final Project myProject;
  private final DocumentEx myHostDocument;
  private final VirtualFile myHostVirtualFile;
  private final PsiElement myContextElement;
  private final PsiFile myHostPsiFile;
  private Thread currentThread;

  InjectionRegistrarImpl(@NotNull Project project, @NotNull PsiFile hostPsiFile, @NotNull PsiElement contextElement) {
    myProject = project;
    myContextElement = contextElement;
    myHostPsiFile = PsiUtilCore.getTemplateLanguageFile(hostPsiFile);
    FileViewProvider viewProvider = myHostPsiFile.getViewProvider();
    myHostVirtualFile = viewProvider.getVirtualFile();
    myHostDocument = (DocumentEx)viewProvider.getDocument();
  }

  @Override
  @Nullable("null means nobody cared to call .doneInjecting()")
  @Deprecated
  public List<Pair<Place, PsiFile>> getResult() {
    return resultFiles == null ? null : resultFiles.stream().map(file -> Pair.create(InjectedLanguageUtil.getShreds(file), file)).collect(Collectors.toList());
  }

  @Nullable
  InjectionResult getInjectedResult() {
    return resultFiles == null && resultReferences == null ? null : new InjectionResult(resultFiles, resultReferences);
  }

  @NotNull
  @Override
  public MultiHostRegistrar startInjecting(@NotNull Language language) {
    return startInjecting(language, null);
  }

  @NotNull
  @Override
  public MultiHostRegistrar startInjecting(@NotNull Language language, @Nullable String extension) {
    fileExtension = extension;
    placeInfos = new SmartList<>();

    if (!cleared) {
      clear();
      throw new IllegalStateException("Seems you haven't called doneInjecting()");
    }
    currentThread = Thread.currentThread();

    if (LanguageParserDefinitions.INSTANCE.forLanguage(language) == null) {
      throw new UnsupportedOperationException("Cannot inject language '" + language + "' because it has no ParserDefinition");
    }
    myLanguage = language;
    return this;
  }

  private void clear() {
    fileExtension = null;
    myLanguage = null;

    cleared = true;
    placeInfos = null;
    currentThread = null;
  }

  private static class PlaceInfo {
    @NotNull private final String prefix;
    @NotNull private final String suffix;
    @NotNull private final PsiLanguageInjectionHost host;
    @NotNull private final TextRange rangeInsideHost;

    PlaceInfo(@NotNull String prefix,
                     @NotNull String suffix,
                     @NotNull PsiLanguageInjectionHost host,
                     @NotNull TextRange rangeInsideHost) {
      this.prefix = prefix;
      this.suffix = suffix;
      this.host = host;
      this.rangeInsideHost = rangeInsideHost;
    }

    @Override
    public String toString() {
      return "Shred "+
             (prefix.isEmpty() ? "" : "prefix='"+prefix+"' ") +
             (suffix.isEmpty() ? "" : "suffix='"+suffix+"' ") +
             "in " + host+ " " +
             "in range "+rangeInsideHost;
    }
  }

  @Override
  @NotNull
  public MultiHostRegistrar addPlace(@NonNls @Nullable String prefix,
                                     @NonNls @Nullable String suffix,
                                     @NotNull PsiLanguageInjectionHost host,
                                     @NotNull TextRange rangeInsideHost) {
    checkThreading();
    if (myLanguage == null) {
      clear();
      throw new IllegalStateException("Seems you haven't called startInjecting()");
    }
    PsiFile containingFile = PsiUtilCore.getTemplateLanguageFile(host);
    assert containingFile == myHostPsiFile : exceptionContext("Trying to inject into foreign file: "+containingFile, myLanguage,
                                                              myHostPsiFile, myHostVirtualFile, myHostDocument, myContextElement, placeInfos);
    TextRange hostTextRange = host.getTextRange();
    if (!hostTextRange.contains(rangeInsideHost.shiftRight(hostTextRange.getStartOffset()))) {
      clear();
      throw new IllegalArgumentException("rangeInsideHost must lie within host text range. rangeInsideHost:"+rangeInsideHost+"; host textRange:"+
                                         hostTextRange);
    }

    cleared = false;
    PlaceInfo info = new PlaceInfo(ObjectUtils.notNull(prefix, ""), ObjectUtils.notNull(suffix, ""), host, rangeInsideHost);
    placeInfos.add(info);

    return this;
  }

  private void checkThreading() {
    if (currentThread != Thread.currentThread()) {
      throw new IllegalStateException("Wow, you must not start injecting in one thread ("+currentThread+") but finish the other");
    }
  }

  @NotNull
  private static Pair.NonNull<ShredImpl, LiteralTextEscaper> createShred(@NotNull Project project, @NotNull PlaceInfo info,
                                                                 @NotNull StringBuilder outChars,
                                                                 @NotNull PsiFile hostPsiFile) {
    int startOffset = outChars.length();
    String prefix = info.prefix;
    outChars.append(prefix);
    PsiLanguageInjectionHost host = info.host;
    LiteralTextEscaper<? extends PsiLanguageInjectionHost> textEscaper = host.createLiteralTextEscaper();

    TextRange rangeInsideHost = info.rangeInsideHost;
    TextRange relevantRange = textEscaper.getRelevantTextRange().intersection(rangeInsideHost);
    if (relevantRange == null) {
      relevantRange = TextRange.from(textEscaper.getRelevantTextRange().getStartOffset(), 0);
    }
    else {
      int before = outChars.length();
      boolean decodeFailed = !textEscaper.decode(relevantRange, outChars);
      int after = outChars.length();
      assert after >= before : "Escaper " + textEscaper + "("+textEscaper.getClass()+") must not mangle char buffer";
      if (decodeFailed) {
        // if there are invalid chars, adjust the range
        int offsetInHost = textEscaper.getOffsetInHost(outChars.length() - before, rangeInsideHost);
        relevantRange = relevantRange.intersection(new ProperTextRange(0, offsetInHost));
      }
    }
    String suffix = info.suffix;
    outChars.append(suffix);
    int endOffset = outChars.length();
    TextRange hostTextRange = host.getTextRange();
    TextRange relevantRangeInHost = relevantRange.shiftRight(hostTextRange.getStartOffset());
    SmartPointerManagerImpl manager = (SmartPointerManagerImpl)SmartPointerManager.getInstance(project);
    ShredImpl shred = new ShredImpl(manager.createSmartPsiFileRangePointer(hostPsiFile, relevantRangeInHost, true),
                                    manager.createSmartPsiElementPointer(host, hostPsiFile, true),
                                    prefix, suffix, new ProperTextRange(startOffset, endOffset), false, textEscaper.isOneLine());
    return Pair.createNonNull(shred, textEscaper);

  }

  @Override
  public void doneInjecting() {
    checkThreading();
    try {
      if (myLanguage == null) {
        throw new IllegalStateException("Seems you haven't called startInjecting()");
      }
      if (placeInfos.isEmpty()) {
        throw new IllegalStateException("Seems you haven't called addPlace()");
      }
      synchronized (InjectedLanguageManagerImpl.ourInjectionPsiLock) {
        Language forcedLanguage = myContextElement.getUserData(InjectedFileViewProvider.LANGUAGE_FOR_INJECTED_COPY_KEY);
        PsiFile psiFile =
          createInjectedFile(myProject, myLanguage, forcedLanguage,
                             myHostDocument, myHostVirtualFile, myHostPsiFile, fileExtension, placeInfos,
                             myContextElement);
        addFileToResults(psiFile);

        PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(myProject);
        DocumentWindowImpl documentWindow = (DocumentWindowImpl)documentManager.getDocument(psiFile);
        assertEverythingIsAllright(documentManager, documentWindow, psiFile);
      }
    }
    finally {
      clear();
    }
  }

  @NotNull
  private static PsiFile createInjectedFile(@NotNull Project project,
                                            @NotNull Language language, @Nullable Language forcedLanguage,
                                            @NotNull DocumentEx hostDocument,
                                            @NotNull VirtualFile hostVirtualFile,
                                            @NotNull PsiFile hostPsiFile,
                                            @Nullable String injectedFileExtension,
                                            @NotNull List<PlaceInfo> placeInfos,
                                            @NotNull PsiElement contextElement) {
    synchronized (InjectedLanguageManagerImpl.ourInjectionPsiLock) {
      PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(project);

      StringBuilder decodedChars = new StringBuilder();
      boolean isOneLine = true;
      Place place = new Place();
      boolean isAncestor = false;
      List<LiteralTextEscaper> escapers = new ArrayList<>(placeInfos.size());
      for (PlaceInfo info : placeInfos) {
        Pair.NonNull<ShredImpl, LiteralTextEscaper> p = createShred(project, info, decodedChars, hostPsiFile);
        ShredImpl shred = p.getFirst();
        isOneLine &= shred.isOneLine();
        place.add(shred);

        isAncestor |= PsiTreeUtil.isAncestor(contextElement, info.host, false);
        escapers.add(p.getSecond());
      }
      assert isAncestor : exceptionContext(contextElement + " must be the parent of at least one of injection hosts", language,
                                           hostPsiFile, hostVirtualFile, hostDocument, contextElement, placeInfos);
      DocumentWindowImpl documentWindow = new DocumentWindowImpl(hostDocument, isOneLine, place);
      String fileName = PathUtil.makeFileName(hostVirtualFile.getName(), injectedFileExtension);
      VirtualFileWindowImpl virtualFile = new VirtualFileWindowImpl(fileName, hostVirtualFile, documentWindow, language, decodedChars);
      Language finalLanguage = forcedLanguage == null ? LanguageSubstitutors.INSTANCE.substituteLanguage(language, virtualFile, project) : forcedLanguage;

      createDocument(virtualFile);

      InjectedFileViewProvider viewProvider = new InjectedFileViewProvider(PsiManager.getInstance(project), virtualFile, documentWindow,
                                                                           finalLanguage);
      ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(finalLanguage);
      assert parserDefinition != null : "Parser definition for language " + finalLanguage + " is null";
      PsiFile psiFile = parserDefinition.createFile(viewProvider);

      SmartPsiElementPointer<PsiLanguageInjectionHost> pointer = ((ShredImpl)place.get(0)).getSmartPointer();

      final ASTNode parsedNode = keepTreeFromChameleoningBack(psiFile);

      assert parsedNode instanceof FileElement : "Parsed to " + parsedNode + " instead of FileElement";

      String documentText = documentManager.getLastCommittedDocument(documentWindow).getText();
      assert ((FileElement)parsedNode).textMatches(decodedChars) : exceptionContext("Before patch: doc:\n'" + documentText + "'\n---PSI:\n'" + parsedNode.getText() + "'\n---chars:\n'" +
                                                                                    decodedChars + "'",
                                                                                    finalLanguage, hostPsiFile, hostVirtualFile,
                                                                                    hostDocument, contextElement, placeInfos);

      viewProvider.doNotInterruptMeWhileImPatchingLeaves(() -> {
        try {
          patchLeaves(parsedNode, place, escapers);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (RuntimeException e) {
          throw new RuntimeException(exceptionContext("Patch error", finalLanguage, hostPsiFile, hostVirtualFile, hostDocument,
                                                      contextElement, placeInfos), e);
        }
      });
      if (!((FileElement)parsedNode).textMatches(documentText)) {
        throw new AssertionError(exceptionContext("After patch: doc:\n'" + documentText + "'\n---PSI:\n'" + parsedNode.getText() + "'\n---chars:\n'" +
                                                  decodedChars + "'",
                                                  finalLanguage, hostPsiFile, hostVirtualFile, hostDocument, contextElement, placeInfos));
      }

      virtualFile.setContent(null, documentWindow.getText(), false);
      virtualFile.setWritable(virtualFile.getDelegate().isWritable());

      cacheEverything(place, documentWindow, viewProvider, psiFile, pointer);

      PsiFile cachedPsiFile = documentManager.getCachedPsiFile(documentWindow);
      assert cachedPsiFile == psiFile : "Cached psi :"+ cachedPsiFile +" instead of "+psiFile;

      assert place.isValid();
      assert viewProvider.isValid();

      PsiFile newFile = registerDocument(documentWindow, psiFile, place, hostPsiFile, documentManager);
      boolean mergeHappened = newFile != psiFile;
      Place mergedPlace = place;
      if (mergeHappened) {
        InjectedLanguageUtil.clearCaches(psiFile, documentWindow);
        psiFile = newFile;
        viewProvider = (InjectedFileViewProvider)psiFile.getViewProvider();
        documentWindow = (DocumentWindowImpl)viewProvider.getDocument();
        virtualFile = (VirtualFileWindowImpl)viewProvider.getVirtualFile();
        boolean shredsReused = !cacheEverything(place, documentWindow, viewProvider, psiFile, pointer);
        if (shredsReused) {
          place.dispose();
          mergedPlace = documentWindow.getShreds();
        }
      }

      assert psiFile.isValid();
      assert mergedPlace.isValid();
      assert viewProvider.isValid();

      try {
        List<Trinity<IElementType, SmartPsiElementPointer<PsiLanguageInjectionHost>, TextRange>>
          tokens = obtainHighlightTokensFromLexer(finalLanguage, decodedChars, place, virtualFile, project, escapers);
        psiFile.putUserData(InjectedLanguageUtil.HIGHLIGHT_TOKENS, tokens);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (RuntimeException e) {
        throw new RuntimeException(exceptionContext("Obtaining tokens error", finalLanguage, hostPsiFile, hostVirtualFile,
                                                    hostDocument, contextElement, placeInfos), e);
      }
      return psiFile;
    }
  }

  public void injectReference(@NotNull Language language,
                              @NotNull String prefix,
                              @NotNull String suffix,
                              @NotNull PsiLanguageInjectionHost host,
                              @NotNull TextRange rangeInsideHost) {
    ParserDefinition parser = LanguageParserDefinitions.INSTANCE.forLanguage(language);
    if (parser != null) {
      throw new IllegalArgumentException("Language "+language+" being injected as reference must not have ParserDefinition and yet - "+parser);
    }
    ReferenceInjector injector = ReferenceInjector.findById(language.getID());
    if (injector == null) {
      throw new IllegalArgumentException("Language "+language+" being injected as reference must register reference injector");
    }
    placeInfos = new SmartList<>();

    if (!cleared) {
      clear();
      throw new IllegalStateException("Seems you haven't called doneInjecting()");
    }

    myLanguage = language;
    currentThread = Thread.currentThread();

    addPlace(prefix, suffix, host, rangeInsideHost);
    Place place = new Place();
    StringBuilder decodedChars = new StringBuilder();
    Pair.NonNull<ShredImpl, LiteralTextEscaper> p = createShred(myProject, placeInfos.get(0), decodedChars, myHostPsiFile);
    place.add(p.getFirst());
    if (resultReferences == null) {
      resultReferences = new SmartList<>();
    }
    resultReferences.add(Pair.create(injector, place));
    clear();
  }
  

  private static void createDocument(@NotNull LightVirtualFile virtualFile) {
    CharSequence content = virtualFile.getContent();
    DocumentImpl document = new DocumentImpl(content, StringUtil.indexOf(content, '\r') >= 0, false);
    FileDocumentManagerImpl.registerDocument(document, virtualFile);
  }

  // returns true if shreds were set, false if old ones were reused
  private static boolean cacheEverything(@NotNull Place place,
                                         @NotNull DocumentWindowImpl documentWindow,
                                         @NotNull InjectedFileViewProvider viewProvider,
                                         @NotNull PsiFile psiFile,
                                         @NotNull SmartPsiElementPointer<PsiLanguageInjectionHost> pointer) {
    FileDocumentManagerImpl.registerDocument(documentWindow, viewProvider.getVirtualFile());

    DebugUtil.startPsiModification("MultiHostRegistrar cacheEverything");
    try {
      viewProvider.forceCachedPsi(psiFile);
    }
    finally {
      DebugUtil.finishPsiModification();
    }

    psiFile.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, pointer);
    ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(psiFile.getProject())).associatePsi(documentWindow, psiFile);

    keepTreeFromChameleoningBack(psiFile);

    return viewProvider.setShreds(place);
  }


  @NonNls
  private static String exceptionContext(@NonNls @NotNull String msg,
                                         @NotNull Language language,
                                         @NotNull PsiFile hostPsiFile,
                                         @NotNull VirtualFile hostVirtualFile,
                                         @NotNull DocumentEx hostDocument,
                                         @NotNull PsiElement contextElement,
                                         @NotNull List<PlaceInfo> placeInfos) {
    return msg + ".\n" +
           language + ";\n " +
           "Host file: " + hostPsiFile + " in '" + hostVirtualFile.getPresentableUrl() + "'" +
           (PsiDocumentManager.getInstance(hostPsiFile.getProject()).isUncommited(hostDocument) ? " (uncommitted)" : "") + "\n" +
           "Context element " + contextElement.getTextRange() + ": '" + contextElement + "'; " +
           "Ranges: " + placeInfos;
  }

  private static final Key<ASTNode> TREE_HARD_REF = Key.create("TREE_HARD_REF");
  private static ASTNode keepTreeFromChameleoningBack(PsiFile psiFile) {
    // need to keep tree reachable to avoid being garbage-collected (via WeakReference in PsiFileImpl)
    // and then being reparsed from wrong (escaped) document content
    ASTNode node = psiFile.getNode();
    // expand chameleons
    ASTNode child = node.getFirstChildNode();

    assert !TreeUtil.isCollapsedChameleon(node) : "Chameleon "+node+" is collapsed; file: "+psiFile+"; language: "+psiFile.getLanguage();
    psiFile.putUserData(TREE_HARD_REF, node);

    // just to use child variable
    if (child == null) {
      assert node != null;
    }
    return node;
  }

  private static void assertEverythingIsAllright(@NotNull PsiDocumentManagerBase documentManager,
                                                 @NotNull DocumentWindowImpl documentWindow,
                                                 @NotNull PsiFile psiFile) {
    InjectedFileViewProvider injectedFileViewProvider = (InjectedFileViewProvider)psiFile.getViewProvider();
    assert injectedFileViewProvider.isValid() : "Invalid view provider: "+injectedFileViewProvider;
    DocumentEx frozenWindow = documentManager.getLastCommittedDocument(documentWindow);
    assert psiFile.textMatches(frozenWindow.getText()) : "Document window text mismatch";
    assert injectedFileViewProvider.getDocument() == documentWindow : "Provider document mismatch";
    assert documentManager.getCachedDocument(psiFile) == documentWindow : "Cached document mismatch";
    assert Comparing.equal(psiFile.getVirtualFile(), injectedFileViewProvider.getVirtualFile()) : "Virtual file mismatch: " +
                                                                                                  psiFile.getVirtualFile() +
                                                                                                  "; " +
                                                                                                  injectedFileViewProvider.getVirtualFile();
    PsiDocumentManagerBase.checkConsistency(psiFile, frozenWindow);
  }

  void addToResults(@NotNull InjectionResult result) {
    if (result.files != null) {
      for (PsiFile file : result.files) {
        addFileToResults(file);
      }
    }
    if (result.references != null) {
      for (Pair<ReferenceInjector, Place> pair : result.references) {
        addReferenceToResults(pair);
      }
    }
  }

  private void addFileToResults(@NotNull PsiFile psiFile) {
    if (resultFiles == null) {
      resultFiles = new SmartList<>();
    }
    resultFiles.add(psiFile);
  }
  private void addReferenceToResults(@NotNull Pair<ReferenceInjector, Place> pair) {
    if (resultReferences == null) {
      resultReferences = new SmartList<>();
    }
    resultReferences.add(pair);
  }


  private static void patchLeaves(@NotNull ASTNode parsedNode,
                                  @NotNull Place shreds,
                                  @NotNull List<LiteralTextEscaper> escapers) {
    LeafPatcher patcher = new LeafPatcher(shreds, escapers);
    ((TreeElement)parsedNode).acceptTree(patcher);

    assert ((TreeElement)parsedNode).textMatches(patcher.catLeafs) : "Malformed PSI structure: leaf texts do not add up to the whole file text." +
                                                  "\nFile text (from tree)  :'"+parsedNode.getText()+"'" +
                                                  "\nFile text (from PSI)   :'"+parsedNode.getPsi().getText()+"'" +
                                                  "\nLeaf texts concatenated:'"+ patcher.catLeafs +"';" +
                                                  "\nFile root: "+parsedNode+
                                                  "\nLanguage: "+parsedNode.getPsi().getLanguage()+
                                                  "\nHost file: "+ shreds.getHostPointer().getVirtualFile()
        ;
    DebugUtil.startPsiModification("injection leaf patching");
    try {
      for (Map.Entry<LeafElement, String> entry : patcher.newTexts.entrySet()) {
        LeafElement leaf = entry.getKey();
        String newText = entry.getValue();
        leaf.rawReplaceWithText(newText);
      }
    }
    finally {
      DebugUtil.finishPsiModification();
    }

    TreeUtil.clearCaches((TreeElement)parsedNode);
  }

  // under InjectedLanguageManagerImpl.ourInjectionPsiLock
  @NotNull
  private static PsiFile registerDocument(@NotNull DocumentWindowImpl newDocumentWindow,
                                          @NotNull PsiFile newInjectedPsi,
                                          @NotNull Place shreds,
                                          @NotNull PsiFile hostPsiFile,
                                          @NotNull PsiDocumentManager documentManager) {
    List<DocumentWindow> injected = InjectedLanguageUtil.getCachedInjectedDocuments(hostPsiFile);

    for (int i = injected.size()-1; i>=0; i--) {
      DocumentWindowImpl oldDocument = (DocumentWindowImpl)injected.get(i);
      final PsiFileImpl oldFile = (PsiFileImpl)documentManager.getCachedPsiFile(oldDocument);
      FileViewProvider viewProvider;
      if (oldFile == null ||
          !oldFile.isValid() ||
          !((viewProvider = oldFile.getViewProvider()) instanceof InjectedFileViewProvider) ||
          ((InjectedFileViewProvider)viewProvider).isDisposed()
        ) {
        injected.remove(i);
        Disposer.dispose(oldDocument);
        continue;
      }

      final ASTNode newInjectedNode = newInjectedPsi.getNode();
      final ASTNode oldFileNode = oldFile.getNode();
      assert newInjectedNode != null : "New node is null";
      if (oldDocument.areRangesEqual(newDocumentWindow)) {
        if (oldFile.getFileType() != newInjectedPsi.getFileType() || oldFile.getLanguage() != newInjectedPsi.getLanguage()) {
          injected.remove(i);
          Disposer.dispose(oldDocument);
          continue;
        }
        oldFile.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, newInjectedPsi.getUserData(FileContextUtil.INJECTED_IN_ELEMENT));

        assert shreds.isValid();
        mergePsi(oldFile, oldFileNode, newInjectedPsi, newInjectedNode);
        assert shreds.isValid();

        return oldFile;
      }
      else if (intersect(oldDocument, newDocumentWindow)) {
        injected.remove(i); // injected fragments should not overlap. In the End, there can be only one.
      }
    }
    injected.add(newDocumentWindow);

    return newInjectedPsi;
  }

  private static void mergePsi(@NotNull PsiFileImpl oldFile,
                               @NotNull ASTNode oldFileNode,
                               @NotNull PsiFile injectedPsi,
                               @NotNull ASTNode injectedNode) {
    if (!oldFile.textMatches(injectedPsi)) {
      InjectedFileViewProvider oldViewProvider = (InjectedFileViewProvider)oldFile.getViewProvider();
      oldViewProvider.performNonPhysically(() -> {
        DebugUtil.startPsiModification("injected tree diff");
        try {
          final DiffLog diffLog = BlockSupportImpl.mergeTrees(oldFile, oldFileNode, injectedNode, new DaemonProgressIndicator(),
                                                              oldFileNode.getText());
          DocumentCommitThread.doActualPsiChange(oldFile, diffLog);
        }
        finally {
          DebugUtil.finishPsiModification();
        }
      });
    }
  }

  private static boolean intersect(DocumentWindowImpl doc1, DocumentWindowImpl doc2) {
    Segment[] hostRanges1 = doc1.getHostRanges();
    Segment[] hostRanges2 = doc2.getHostRanges();
    // DocumentWindowImpl.getHostRanges() may theoretically return non-sorted ranges
    for (Segment segment1 : hostRanges1) {
      for (Segment segment2 : hostRanges2) {
        if (Math.max(segment1.getStartOffset(), segment2.getStartOffset()) < Math.min(segment1.getEndOffset(), segment2.getEndOffset())) {
          return true;
        }
      }
    }
    return false;
  }

  // returns lexer element types with corresponding ranges in encoded (injection host based) PSI
  @NotNull
  private static List<Trinity<IElementType, SmartPsiElementPointer<PsiLanguageInjectionHost>, TextRange>>
          obtainHighlightTokensFromLexer(@NotNull Language language,
                                         @NotNull CharSequence outChars,
                                         @NotNull Place shreds,
                                         @NotNull VirtualFileWindow virtualFile,
                                         @NotNull Project project,
                                         @NotNull List<LiteralTextEscaper> escapers) {
    SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, (VirtualFile)virtualFile);
    Lexer lexer = syntaxHighlighter.getHighlightingLexer();
    lexer.start(outChars);
    int hostNum = -1;
    int prevHostEndOffset = 0;
    SmartPsiElementPointer<PsiLanguageInjectionHost> hostPtr = null;
    LiteralTextEscaper escaper = null;
    int prefixLength = 0;
    int suffixLength = 0;
    TextRange rangeInsideHost = null;
    int shredEndOffset = -1;
    List<Trinity<IElementType, SmartPsiElementPointer<PsiLanguageInjectionHost>, TextRange>> tokens = new ArrayList<>(10);
    for (IElementType tokenType = lexer.getTokenType(); tokenType != null; lexer.advance(), tokenType = lexer.getTokenType()) {
      TextRange range = new ProperTextRange(lexer.getTokenStart(), lexer.getTokenEnd());
      while (range != null && !range.isEmpty()) {
        if (range.getStartOffset() >= shredEndOffset) {
          hostNum++;
          PsiLanguageInjectionHost.Shred shred = shreds.get(hostNum);
          shredEndOffset = shred.getRange().getEndOffset();
          prevHostEndOffset = range.getStartOffset();
          hostPtr = ((ShredImpl)shred).getSmartPointer();
          escaper = escapers.get(hostNum);
          rangeInsideHost = shred.getRangeInsideHost();
          prefixLength = shred.getPrefix().length();
          suffixLength = shred.getSuffix().length();
        }
        //in prefix/suffix or spills over to next fragment
        if (range.getStartOffset() < prevHostEndOffset + prefixLength) {
          range = new UnfairTextRange(prevHostEndOffset + prefixLength, range.getEndOffset());
        }
        TextRange spilled = null;
        if (range.getEndOffset() > shredEndOffset - suffixLength) {
          spilled = new UnfairTextRange(shredEndOffset, range.getEndOffset());
          range = new UnfairTextRange(range.getStartOffset(), shredEndOffset-suffixLength);
        }
        if (!range.isEmpty()) {
          int start = escaper.getOffsetInHost(range.getStartOffset() - prevHostEndOffset - prefixLength, rangeInsideHost);
          if (start == -1) start = rangeInsideHost.getStartOffset();
          int end = escaper.getOffsetInHost(range.getEndOffset() - prevHostEndOffset - prefixLength, rangeInsideHost);
          if (end == -1) {
            end = rangeInsideHost.getEndOffset();
            prevHostEndOffset = shredEndOffset;
          }
          TextRange rangeInHost = new ProperTextRange(start, end);
          tokens.add(Trinity.create(tokenType, hostPtr, rangeInHost));
        }
        range = spilled;
      }
    }
    return tokens;
  }

  @Override
  public String toString() {
    return String.valueOf(resultFiles);
  }

  // performance: avoid context.getContainingFile()
  @NotNull
  PsiFile getHostPsiFile() {
    return myHostPsiFile;
  }
}
