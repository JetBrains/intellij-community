package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.ParameterInfoController;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.ui.popup.JBPopupImpl;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.List;

public class JavaDocManager implements ProjectComponent {
  @NonNls public static final String JAVADOC_LOCATION_AND_SIZE = "javadoc.popup";
  private final Project myProject;
  private Editor myEditor = null;
  private ParameterInfoController myParameterInfoController;
  private Alarm myUpdateDocAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private WeakReference<JBPopup> myDocInfoHintRef;
  private Component myPreviouslyFocused = null;
  public static final Key<SmartPsiElementPointer> ORIGINAL_ELEMENT_KEY = Key.create("Original element");
  public static final @NonNls String HTML_EXTENSION = ".html";
  public static final @NonNls String PACKAGE_SUMMARY_FILE = "package-summary.html";
  public static final @NonNls String PSI_ELEMENT_PROTOCOL = "psi_element://";
  public static final @NonNls String DOC_ELEMENT_PROTOCOL = "doc_element://";

  private ActionManagerEx myActionManagerEx;
  private AnActionListener myActionListener = new AnActionListener() {
    public void beforeActionPerformed(AnAction action, DataContext dataContext) {
      final JBPopup hint = getDocInfoHint();
      if (hint != null) {
        if (action instanceof HintManager.ActionToIgnore) return;
        if (action == myActionManagerEx.getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)) return;
        if (action == myActionManagerEx.getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)) return;
        if (action == myActionManagerEx.getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN)) return;
        if (action == myActionManagerEx.getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP)) return;
        if (action == ActionManagerEx.getInstanceEx().getAction(IdeActions.ACTION_EDITOR_ESCAPE)) return;
        hint.cancel();
      }
    }

    public void beforeEditorTyping(char c, DataContext dataContext) {
      final JBPopup hint = getDocInfoHint();
      if (hint != null) {
        hint.cancel();
      }
    }


    public void afterActionPerformed(final AnAction action, final DataContext dataContext) {
    }
  };
  private static int ourFlagsForTargetElements = TargetElementUtil.ELEMENT_NAME_ACCEPTED
                                                             | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
  | TargetElementUtil.LOOKUP_ITEM_ACCEPTED
  | TargetElementUtil.NEW_AS_CONSTRUCTOR
  | TargetElementUtil.THIS_ACCEPTED
  | TargetElementUtil.SUPER_ACCEPTED;

  public static JavaDocManager getInstance(Project project) {
    return project.getComponent(JavaDocManager.class);
  }

  public JavaDocManager(Project project, ActionManagerEx managerEx) {
    myProject = project;
    myActionManagerEx = managerEx;
  }

  @NotNull
  public String getComponentName() {
    return "JavaDocManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void projectOpened() {
    myActionManagerEx.addAnActionListener(myActionListener);
  }

  public void projectClosed() {
    myActionManagerEx.removeAnActionListener(myActionListener);
  }

  public JBPopup showJavaDocInfo(@NotNull PsiElement element) {
    final JavaDocInfoComponent component = new JavaDocInfoComponent(this);


    final String title = SymbolPresentationUtil.getSymbolPresentableText(element);
    final JBPopup hint = JBPopupFactory.getInstance().createComponentPopupBuilder(component, component)
      .setRequestFocusIfNotLookupOrSearch(getProject(element))
      .setLookupAndSearchUpdater(new Condition<PsiElement>() {
        public boolean value(final PsiElement element) {
          showJavaDocInfo(element);
          return false;
        }
      }, getProject(element))
      .setForceHeavyweight(true)
      .setDimensionServiceKey(myProject, JAVADOC_LOCATION_AND_SIZE, false)
      .setResizable(true)
      .setMovable(true)
      .setTitle(CodeInsightBundle.message("javadoc.info.title", title != null ? title : element.getText()))
      .setCancelCallback(new Computable<Boolean>() {
        public Boolean compute() {
          if (fromQuickSearch()) {
            ((ChooseByNameBase.JPanelProvider)myPreviouslyFocused.getParent()).unregisterHint();
          }

          Disposer.dispose(component);

          myEditor = null;
          myPreviouslyFocused = null;
          return Boolean.TRUE;
        }
      })
      .createPopup();


    JBPopupImpl oldHint = (JBPopupImpl)getDocInfoHint();

    if (oldHint != null) {
      JavaDocInfoComponent oldComponent = (JavaDocInfoComponent)oldHint.getComponent();
      PsiElement element1 = oldComponent.getElement();
      if (Comparing.equal(element, element1)) {
        return oldHint;
      }
      oldHint.cancel();
    }

    component.setHint(hint);

    fetchDocInfo(getDefaultProvider(element), component);

    myDocInfoHintRef = new WeakReference<JBPopup>(hint);

    myPreviouslyFocused = WindowManagerEx.getInstanceEx().getFocusedComponent(getProject(element));

    if (fromQuickSearch()) {
      ((ChooseByNameBase.JPanelProvider)myPreviouslyFocused.getParent()).registerHint(hint);
    }

    return hint;
  }

  public JBPopup showJavaDocInfo(final Editor editor, PsiFile file, boolean requestFocus) {
    myEditor = editor;
    final Project project = getProject(file);
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final PsiExpressionList list =
      ParameterInfoController.findArgumentList(file, editor.getCaretModel().getOffset(), -1);
    if (list != null) {
      myParameterInfoController = ParameterInfoController.getControllerAtOffset(editor, list.getTextRange().getStartOffset());
    }

    PsiElement originalElement = (file != null)?file.findElementAt(editor.getCaretModel().getOffset()): null;
    PsiElement element = findTargetElement(editor, file, originalElement);

    if (element instanceof PsiAnonymousClass) {
      element = ((PsiAnonymousClass)element).getBaseClassType().resolve();
    }

    if (element == null && myParameterInfoController != null) {
      final Object[] objects = myParameterInfoController.getSelectedElements();

      if (objects != null && objects.length > 0) {
        element = getPsiElementFromParameterInfoObject(objects[0], element);
      }
    }

    if (element == null && file != null) { // look if we are within a javadoc comment
      element = originalElement;
      if (element == null) return null;
      PsiDocComment comment = PsiTreeUtil.getParentOfType(element, PsiDocComment.class);
      if (comment == null) return null;
      element = comment.getParent();
      if (!(element instanceof PsiDocCommentOwner)) return null;
    }

    JBPopupImpl oldHint = (JBPopupImpl)getDocInfoHint();
    if (oldHint != null) {
      JavaDocInfoComponent component = (JavaDocInfoComponent)oldHint.getComponent();
      PsiElement element1 = component.getElement();
      if (element != null && Comparing.equal(element, element1)) {
        if (requestFocus) {
          component.getComponent().requestFocus();
        }
        return oldHint;
      }
      oldHint.cancel();
    }

    final JavaDocInfoComponent component = new JavaDocInfoComponent(this);
    try {
      element.putUserData(
        ORIGINAL_ELEMENT_KEY,
        SmartPointerManager.getInstance(project).createSmartPsiElementPointer(originalElement)
      );
    } catch (RuntimeException ex) {
      // PsiPackage does not allow putUserData
    }

    final String title = SymbolPresentationUtil.getSymbolPresentableText(element);
    final JBPopup hint = JBPopupFactory.getInstance().createComponentPopupBuilder(component, component)
      .setRequestFocusIfNotLookupOrSearch(project)
      .setLookupAndSearchUpdater(new Condition<PsiElement>() {
        public boolean value(final PsiElement element) {
          if (myEditor != null){
            final PsiFile file = element.getContainingFile();
            if (file != null) {
              Editor editor = myEditor;
              showJavaDocInfo(myEditor, file, false);
              myEditor = editor;
            }
          } else {
            showJavaDocInfo(element);
          }
          return false;
        }
      }, project)
      .setForceHeavyweight(false)
      .setDimensionServiceKey(project, JAVADOC_LOCATION_AND_SIZE, false)
      .setResizable(true)
      .setMovable(true)
      .setTitle(CodeInsightBundle.message("javadoc.info.title", title != null ? title : element.getText()))
      .setCancelCallback(new Computable<Boolean>(){
        public Boolean compute() {
          if (fromQuickSearch()) {
            ((ChooseByNameBase.JPanelProvider)myPreviouslyFocused.getParent()).unregisterHint();
          }

          Disposer.dispose(component);
          myEditor = null;
          myPreviouslyFocused = null;
          myParameterInfoController = null;
          return Boolean.TRUE;
        }
      })
      .createPopup();


    component.setHint(hint);

    fetchDocInfo(getDefaultProvider(element), component);

    myDocInfoHintRef = new WeakReference<JBPopup>(hint);
    myPreviouslyFocused = WindowManagerEx.getInstanceEx().getFocusedComponent(project);

    return hint;
  }

  @Nullable
  public static PsiElement findTargetElement(final Editor editor, final PsiFile file, PsiElement contextElement) {
    PsiElement element = TargetElementUtil.findTargetElement(editor, ourFlagsForTargetElements);

    // Allow context doc over xml tag content
    if (element == null && contextElement != null) {
        final PsiElement parent = contextElement.getParent();
        if (parent instanceof XmlText) {
          element = TargetElementUtil.findTargetElement(editor, ourFlagsForTargetElements,
                                                             parent.getParent().getTextRange().getStartOffset() + 1
                                                           );
        } else if (parent instanceof XmlTag) {
          element = TargetElementUtil.findTargetElement(editor, ourFlagsForTargetElements,
                                                             parent.getTextRange().getStartOffset() + 1
                                                           );
        }
    }

    if (element == null && editor != null) {
      final PsiReference ref = TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset());

      if (ref != null) {
        final PsiElement parent = ref.getElement().getParent();

        if (parent instanceof PsiMethodCallExpression) {
          element = parent;
        } else if (ref instanceof PsiPolyVariantReference) {
          element = ref.getElement();
        }
      }

      final Lookup activeLookup = LookupManager.getInstance(file.getProject()).getActiveLookup();

      if (activeLookup != null) {
        LookupItem item = activeLookup.getCurrentItem();
        if (item == null) return null;

        final DocumentationProvider documentationProvider = getProviderFromElement(file);

        if (documentationProvider!=null) {
          element = documentationProvider.getDocumentationElementForLookupItem(
            file.getManager(),
            item.getObject(),
            ref != null ? ref.getElement():contextElement
          );
        }
      }
    }

    return element;
  }

  private boolean fromQuickSearch() {
    return myPreviouslyFocused != null && myPreviouslyFocused.getParent() instanceof ChooseByNameBase.JPanelProvider;
  }

  private static PsiElement getPsiElementFromParameterInfoObject(final Object o, PsiElement element) {
    if (o instanceof CandidateInfo) {
      element = ((CandidateInfo)o).getElement();
    } else if (o instanceof PsiElement) {
      element = (PsiElement)o;
    }
    return element;
  }

  public JavaDocProvider getDefaultProvider(final PsiElement _element) {
    return new JavaDocProvider() {
      private SmartPsiElementPointer element = SmartPointerManager.getInstance(_element.getProject()).createSmartPsiElementPointer(_element);

      public String getJavaDoc() throws Exception {
        return getDocInfo(element.getElement());
      }

      public PsiElement getElement() {
        return element.getElement();
      }
    };
  }

  public static String getExternalJavaDocUrl(final PsiElement element) {
    String url = null;

    if (element instanceof PsiClass) {
      url = findUrlForClass((PsiClass)element);
    }
    else if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      PsiClass aClass = field.getContainingClass();
      if (aClass != null) {
        url = findUrlForClass(aClass);
        if (url != null) {
          url += "#" + field.getName();
        }
      }
    }
    else if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        url = findUrlForClass(aClass);
        if (url != null) {
          String signature = PsiFormatUtil.formatMethod(method,
                                                        PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME |
                                                                              PsiFormatUtil.SHOW_PARAMETERS,
                                                        PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_FQ_CLASS_NAMES, 999);
          url += "#" + signature;
        }
      }
    }
    else if (element instanceof PsiPackage) {
      url = findUrlForPackage((PsiPackage)element);
    }
    else if (element instanceof PsiDirectory) {
      PsiPackage aPackage = ((PsiDirectory)element).getPackage();
      if (aPackage != null) {
        url = findUrlForPackage(aPackage);
      }
    } else {
      DocumentationProvider provider = getProviderFromElement(element);
      if (provider!=null) {
        final SmartPsiElementPointer originalElementPointer = element.getUserData(ORIGINAL_ELEMENT_KEY);
        url = provider.getUrlFor(element, originalElementPointer != null ? originalElementPointer.getElement() : null);
      }
    }

    return url == null ? null : url.replace('\\', '/');
  }

  public void openJavaDoc(final PsiElement element) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.javadoc.external");
    String url = getExternalJavaDocUrl(element);
    if (url != null) {
      BrowserUtil.launchBrowser(url);
    }
    else {
      final JBPopup docInfoHint = getDocInfoHint();
      if (docInfoHint != null && docInfoHint.isVisible()){
        docInfoHint.cancel();
      }
      Messages.showMessageDialog(getProject(element),
                                 CodeInsightBundle.message("javadoc.documentation.not.found.message"),
                                 CodeInsightBundle.message("javadoc.documentation.not.found.title"),
                                 Messages.getErrorIcon());
    }
  }

  public JBPopup getDocInfoHint() {
    if (myDocInfoHintRef == null) return null;
    JBPopup hint = myDocInfoHintRef.get();
    if (hint == null || !hint.isVisible()) {
      myDocInfoHintRef = null;
      return null;
    }
    return hint;
  }

  private static String findUrlForClass(PsiClass aClass) {
    String qName = aClass.getQualifiedName();
    if (qName == null) return null;
    PsiFile file = aClass.getContainingFile();
    if (!(file instanceof PsiJavaFile)) return null;
    String packageName = ((PsiJavaFile)file).getPackageName();

    String relPath;
    if (packageName.length() > 0) {
      relPath = packageName.replace('.', '/') + '/' + qName.substring(packageName.length() + 1) + HTML_EXTENSION;
    }
    else {
      relPath = qName + HTML_EXTENSION;
    }

    final PsiFile containingFile = aClass.getContainingFile();
    if (containingFile == null) return null;
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) return null;

    return findUrlForVirtualFile(containingFile.getProject(), virtualFile, relPath);
  }

  private static String findUrlForVirtualFile(final Project project, final VirtualFile virtualFile, final String relPath) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Module module = fileIndex.getModuleForFile(virtualFile);
    if (module == null) {
      final VirtualFileSystem fs = virtualFile.getFileSystem();
      if (fs instanceof JarFileSystem) {
        final VirtualFile jar = ((JarFileSystem)fs).getVirtualFileForJar(virtualFile);
        if (jar != null) {
          module = fileIndex.getModuleForFile(jar);
        }
      }
    }
    if (module != null) {
      VirtualFile[] javadocPaths = ModuleRootManager.getInstance(module).getJavadocPaths();
      String httpRoot = getHttpRoot(javadocPaths, relPath);
      if (httpRoot != null) return httpRoot;
    }

    final List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(virtualFile);
    for (OrderEntry orderEntry : orderEntries) {
      final VirtualFile[] files = orderEntry.getFiles(OrderRootType.JAVADOC);
      final String httpRoot = getHttpRoot(files, relPath);
      if (httpRoot != null) return httpRoot;
    }
    return null;
  }

  private static String getHttpRoot(final VirtualFile[] roots, String relPath) {
    for (VirtualFile root : roots) {
      if (root.getFileSystem() instanceof HttpFileSystem) {
        return root.getUrl() + relPath;
      }
      else {
        VirtualFile file = root.findFileByRelativePath(relPath);
        if (file != null) return file.getUrl();
      }
    }

    return null;
  }

  private static String findUrlForPackage(PsiPackage aPackage) {
    String qName = aPackage.getQualifiedName();
    qName = qName.replace('.', '/') +  '/' + PACKAGE_SUMMARY_FILE;
    for(PsiDirectory directory: aPackage.getDirectories()) {
      String url = findUrlForVirtualFile(aPackage.getProject(),directory.getVirtualFile(), qName);
      if (url != null) {
        return url;
      }
    }
    return null;
  }

  private String findUrlForLink(PsiPackage basePackage, String link) {
    int index = link.indexOf('#');
    String tail = "";
    if (index >= 0) {
      tail = link.substring(index);
      link = link.substring(0, index);
    }

    String qName = basePackage.getQualifiedName();
    qName = qName.replace('.', File.separatorChar);
    String[] docPaths = JavaDocUtil.getDocPaths(getProject(basePackage));
    for (String docPath : docPaths) {
      String url = docPath + File.separator + qName + File.separatorChar + link;
      File file = new File(url);
      if (file.exists()) return url + tail;
    }
    return null;
  }

  public void fetchDocInfo(final JavaDocProvider provider, final JavaDocInfoComponent component) {
    doFetchDocInfo(component, provider, true);
  }

  public void queueFetchDocInfo(final JavaDocProvider provider, final JavaDocInfoComponent component) {
    doFetchDocInfo(component, provider, false);
  }

  private void doFetchDocInfo(final JavaDocInfoComponent component, final JavaDocProvider provider, final boolean cancelRequests) {
    component.startWait();
    if (cancelRequests) {
      myUpdateDocAlarm.cancelAllRequests();
    }
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (component.isEmpty()) {
          component.setText(CodeInsightBundle.message("javadoc.fetching.progress"));
        }
      }
    });
    myUpdateDocAlarm.addRequest(new Runnable() {
      public void run() {
        final String text;
        final Exception[] ex = new Exception[1];
        text = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
          @Nullable
          public String compute() {
            try {
              return provider.getJavaDoc();
            }
            catch (Exception e) {
              ex[0] = e;
            }
            return null;
          }
        });
        if (ex[0] != null) {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              component.setText(CodeInsightBundle.message("javadoc.external.fetch.error.message", ex[0].getLocalizedMessage()), true);
            }
          });
          return;
        }

        final PsiElement element = provider.getElement();
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {

            if (text == null) {
              component.setText(CodeInsightBundle.message("no.documentation.found"), true);
            }
            else if (text.length() == 0) {
              component.setText(component.getText(), true);
            }
            else {
              component.setData(element, text);
            }

            final JBPopupImpl jbPopup = (JBPopupImpl)getDocInfoHint();
            if(jbPopup==null){
              return;
            }
            final String dimensionServiceKey = jbPopup.getDimensionServiceKey();
            Dimension dimension = component.getPreferredSize();
            final Dimension storedSize = dimensionServiceKey != null ? DimensionService.getInstance().getSize(dimensionServiceKey, getProject(element)) : null;
            if (storedSize != null) {
              dimension = storedSize;
            }
            final Window window = SwingUtilities.getWindowAncestor(component);
            if (window != null) {
              window.setBounds(window.getX(), window.getY(), dimension.width, dimension.height);
              window.validate();
              window.repaint();
            }
          }
        });
      }
    }, 10);
  }

  @Nullable
  private String getDocInfo(PsiElement element) throws Exception {
    if (element instanceof PsiMethodCallExpression) {
      return getMethodCandidateInfo(((PsiMethodCallExpression)element));
    }
    else {
      final DocumentationProvider provider = getProviderFromElement(element);
      final JavaDocInfoGenerator javaDocInfoGenerator =
        new JavaDocInfoGenerator(getProject(element), element, provider);

      if (myParameterInfoController != null) {
        final Object[] objects = myParameterInfoController.getSelectedElements();

        if (objects.length > 0) {
          @NonNls StringBuffer sb = null;

          for(Object o:objects) {
            PsiElement parameter = getPsiElementFromParameterInfoObject(o, null);

            if (parameter != null) {
              if (sb == null) sb = new StringBuffer();
              final String str2 = new JavaDocInfoGenerator(getProject(element), parameter, provider).generateDocInfo();
              sb.append(str2);
              sb.append("<br>");
            } else {
              sb = null;
              break;
            }
          }

          if (sb != null) return sb.toString();
        }
      }

      JavaDocExternalFilter docFilter = new JavaDocExternalFilter(getProject(element));
      String docURL = getExternalJavaDocUrl(element);

      if (element instanceof PsiCompiledElement) {
        try {
          String externalDoc = docFilter.getExternalDocInfoForElement(docURL, element);
          if (externalDoc != null) {
            return externalDoc;
          }
        }
        catch (FileNotFoundException e) {
          //try to generate some javadoc
        }
      }

      return docFilter.filterInternalDocInfo(javaDocInfoGenerator.generateDocInfo(), docURL);
    }
  }

  public static @Nullable DocumentationProvider getProviderFromElement(final PsiElement element) {
    SmartPsiElementPointer originalElementPointer = element!=null ? element.getUserData(ORIGINAL_ELEMENT_KEY):null;
    PsiElement originalElement = originalElementPointer != null ? originalElementPointer.getElement() : null;
    PsiFile containingFile = (originalElement!=null)?originalElement.getContainingFile() : (element!=null)?element.getContainingFile():null;

    return containingFile != null ? containingFile.getLanguage().getDocumentationProvider():null;
  }

  private String getMethodCandidateInfo(PsiMethodCallExpression expr) {
    final PsiResolveHelper rh = expr.getManager().getResolveHelper();
    final CandidateInfo[] candidates = rh.getReferencedMethodCandidates(expr, true);

    final String text = expr.getText();
    if (candidates.length > 0) {
      final @NonNls StringBuffer sb = new StringBuffer();

      for (final CandidateInfo candidate : candidates) {
        final PsiElement element = candidate.getElement();

        if (!(element instanceof PsiMethod)) {
          continue;
        }

        final String str = PsiFormatUtil.formatMethod(((PsiMethod)element), candidate.getSubstitutor(),
                                                      PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_PARAMETERS,
                                                      PsiFormatUtil.SHOW_TYPE);
        createElementLink(sb, element, str, null);
      }

      return CodeInsightBundle.message("javadoc.candiates", text, sb);
    }

    return CodeInsightBundle.message("javadoc.candidates.not.found", text);
  }

  private void createElementLink(final @NonNls StringBuffer sb, final PsiElement element, final String str,final String str2) {
    sb.append("&nbsp;&nbsp;<a href=\"psi_element://");
    sb.append(JavaDocUtil.getReferenceText(getProject(element), element));
    sb.append("\">");
    sb.append(str);
    sb.append("</a>");
    if (str2 != null) sb.append(str2);
    sb.append("<br>");
  }

  void navigateByLink(final JavaDocInfoComponent component, String url) {
    component.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    final PsiManager manager = PsiManager.getInstance(getProject(component.getElement()));
    if (url.startsWith(PSI_ELEMENT_PROTOCOL)) {
      final String refText = url.substring(PSI_ELEMENT_PROTOCOL.length());
      final PsiElement targetElement = JavaDocUtil.findReferenceTarget(manager, refText, component.getElement());
      if (targetElement != null) {
        fetchDocInfo(getDefaultProvider(targetElement), component);
      }
    }
    else {
      final String docUrl = url;

      fetchDocInfo
        (new JavaDocProvider() {
          String getElementLocator(String url) {
            if (url.startsWith(DOC_ELEMENT_PROTOCOL)) {
              return url.substring(DOC_ELEMENT_PROTOCOL.length());
            }
            return null;
          }

          public String getJavaDoc() throws Exception {
            String url = getElementLocator(docUrl);
            if (url != null && JavaDocExternalFilter.isJavaDocURL(url)) {
              String text = new JavaDocExternalFilter(getProject(component.getElement())).getExternalDocInfo(url);

              if (text != null) {
                return text;
              }
            }

            if (url == null) {
              url = docUrl;
            }

            PsiElement element = component.getElement();
            if (element != null) {
              PsiElement parent = element;
              while (true) {
                if (parent == null || parent instanceof PsiDirectory) break;
                parent = parent.getParent();
              }
              if (parent != null) {
                PsiPackage aPackage = ((PsiDirectory)parent).getPackage();
                if (aPackage != null) {
                  String url1 = findUrlForLink(aPackage, url);
                  if (url1 != null) {
                    url = url1;
                  }
                }
              }
            }

            BrowserUtil.launchBrowser(url);

            return "";
          }

          public PsiElement getElement() {
            //String loc = getElementLocator(docUrl);
            //
            //if (loc != null) {
            //  PsiElement context = component.getElement();
            //  return JavaDocUtil.findReferenceTarget(context.getManager(), loc, context);
            //}

            return component.getElement();
          }
        }, component);
    }

    component.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
  }

  void showHint(final JBPopup hint) {
    if (myEditor != null) {
      hint.showInBestPositionFor(myEditor);
    }
    else if (myPreviouslyFocused != null) {
      hint.showInBestPositionFor(DataManager.getInstance().getDataContext(myPreviouslyFocused));
    }
  }

  public void requestFocus() {
    if (fromQuickSearch()) {
      myPreviouslyFocused.getParent().requestFocus();
    }
  }

  public Project getProject(final @Nullable PsiElement element) {
    assert element == null || myProject == element.getProject();
    return myProject;
  }
}