package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.ParameterInfoController;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.ExtensibleDocumentationProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.popup.JBPopupImpl;
import com.intellij.ui.popup.NotLookupOrSearchCondition;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.LinkedHashSet;
import java.util.Set;

public class DocumentationManager implements ProjectComponent {
  @NonNls public static final String JAVADOC_LOCATION_AND_SIZE = "javadoc.popup";
  private final Project myProject;
  private Editor myEditor = null;
  private ParameterInfoController myParameterInfoController;
  private final Alarm myUpdateDocAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private WeakReference<JBPopup> myDocInfoHintRef;
  private Component myPreviouslyFocused = null;
  public static final Key<SmartPsiElementPointer> ORIGINAL_ELEMENT_KEY = Key.create("Original element");
  @NonNls public static final String PSI_ELEMENT_PROTOCOL = "psi_element://";
  @NonNls private static final String DOC_ELEMENT_PROTOCOL = "doc_element://";

  private final ActionManagerEx myActionManagerEx;
  private final AnActionListener myActionListener = new AnActionListener() {
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

  private static final int ourFlagsForTargetElements = TargetElementUtilBase.getInstance().getAllAccepted();

  public static DocumentationManager getInstance(Project project) {
    return project.getComponent(DocumentationManager.class);
  }

  public DocumentationManager(Project project, ActionManagerEx managerEx) {
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
    final DocumentationComponent component = new DocumentationComponent(this);

    final PopupUpdateProcessor updateProcessor = new PopupUpdateProcessor(new Condition<PsiElement>() {
      public boolean value(final PsiElement element) {
        showJavaDocInfo(element);
        return false;
      }
    });
    final JBPopup hint = JBPopupFactory.getInstance().createComponentPopupBuilder(component, component)
      .setRequestFocusCondition(getProject(element), NotLookupOrSearchCondition.INSTANCE)
      .setProject(myProject)
      .addListener(updateProcessor)
      .addUserData(updateProcessor)
      .setForceHeavyweight(true)
      .setDimensionServiceKey(myProject, JAVADOC_LOCATION_AND_SIZE, false)
      .setResizable(true)
      .setMovable(true)
      .setTitle(getTitle(element))
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
      DocumentationComponent oldComponent = (DocumentationComponent)oldHint.getComponent();
      PsiElement element1 = oldComponent.getElement();
      if (Comparing.equal(element, element1)) {
        return oldHint;
      }
      oldHint.cancel();
    }

    component.setHint(hint);

    fetchDocInfo(getDefaultCollector(element), component);

    myDocInfoHintRef = new WeakReference<JBPopup>(hint);

    myPreviouslyFocused = WindowManagerEx.getInstanceEx().getFocusedComponent(getProject(element));

    if (fromQuickSearch()) {
      ((ChooseByNameBase.JPanelProvider)myPreviouslyFocused.getParent()).registerHint(hint);
    }

    return hint;
  }

  @Nullable
  public JBPopup showJavaDocInfo(final Editor editor, @Nullable PsiFile file, boolean requestFocus) {
    myEditor = editor;
    final Project project = getProject(file);
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final PsiElement list =
      ParameterInfoController.findArgumentList(file, editor.getCaretModel().getOffset(), -1);
    if (list != null) {
      myParameterInfoController = ParameterInfoController.findControllerAtOffset(editor, list.getTextRange().getStartOffset());
    }

    PsiElement originalElement = file != null ? file.findElementAt(editor.getCaretModel().getOffset()) : null;
    PsiElement element = findTargetElement(editor, file, originalElement);

    if (element == null && myParameterInfoController != null) {
      final Object[] objects = myParameterInfoController.getSelectedElements();

      if (objects != null && objects.length > 0) {
        if (objects[0] instanceof PsiElement) {
          element = (PsiElement)objects[0];
        }
      }
    }

    if (element == null && file == null) return null; //file == null for text field editor

    if (element == null) { // look if we are within a javadoc comment
      element = originalElement;
      if (element == null) return null;
      PsiComment comment = PsiTreeUtil.getParentOfType(element, PsiComment.class);
      if (comment == null) return null;
      element = comment.getParent();
      //if (!(element instanceof PsiDocCommentOwner)) return null;
    }

    JBPopupImpl oldHint = (JBPopupImpl)getDocInfoHint();
    if (oldHint != null) {
      DocumentationComponent component = (DocumentationComponent)oldHint.getComponent();
      PsiElement element1 = component.getElement();
      if (Comparing.equal(element, element1)) {
        if (requestFocus) {
          component.getComponent().requestFocus();
        }
        return oldHint;
      }
      oldHint.cancel();
    }

    final DocumentationComponent component = new DocumentationComponent(this);
    storeOriginalElement(project, originalElement, element);

    final PopupUpdateProcessor updateProcessor = new PopupUpdateProcessor(new Condition<PsiElement>() {
      public boolean value(final PsiElement element) {
        if (myEditor != null) {
          final PsiFile file = element.getContainingFile();
          if (file != null) {
            Editor editor = myEditor;
            showJavaDocInfo(myEditor, file, false);
            myEditor = editor;
          }
        }
        else {
          showJavaDocInfo(element);
        }
        return false;
      }
    });
    final JBPopup hint = JBPopupFactory.getInstance().createComponentPopupBuilder(component, component)
      .setRequestFocusCondition(project, NotLookupOrSearchCondition.INSTANCE)
      .setProject(project)
      .addListener(updateProcessor)
      .addUserData(updateProcessor)
      .setForceHeavyweight(false)
      .setDimensionServiceKey(project, JAVADOC_LOCATION_AND_SIZE, false)
      .setResizable(true)
      .setMovable(true)
      .setTitle(getTitle(element))
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

    fetchDocInfo(getDefaultCollector(element), component);

    myDocInfoHintRef = new WeakReference<JBPopup>(hint);
    myPreviouslyFocused = WindowManagerEx.getInstanceEx().getFocusedComponent(project);

    return hint;
  }

  private static String getTitle(final PsiElement element) {
    final String title = SymbolPresentationUtil.getSymbolPresentableText(element);
    return CodeInsightBundle.message("javadoc.info.title", title != null ? title : element.getText());
  }

  public static void storeOriginalElement(final Project project, final PsiElement originalElement, final PsiElement element) {
    if (element == null) return;
    try {
      element.putUserData(
        ORIGINAL_ELEMENT_KEY,
        SmartPointerManager.getInstance(project).createSmartPsiElementPointer(originalElement)
      );
    } catch (RuntimeException ex) {
      // PsiPackage does not allow putUserData
    }
  }

  @Nullable
  public PsiElement findTargetElement(final Editor editor, @Nullable final PsiFile file, PsiElement contextElement) {
    PsiElement element = editor != null ? TargetElementUtilBase.findTargetElement(editor, ourFlagsForTargetElements) : null;

    // Allow context doc over xml tag content
    if (element != null || contextElement != null) {
      final PsiElement adjusted = TargetElementUtilBase.getInstance()
        .adjustElement(editor, ourFlagsForTargetElements, element, contextElement);
      if (adjusted != null) {
        element = adjusted;
      }
    }
    if (element == null && editor != null) {
      final PsiReference ref = TargetElementUtilBase.findReference(editor, editor.getCaretModel().getOffset());

      if (ref != null) {
        element = TargetElementUtilBase.getInstance().adjustReference(ref);
        if (element == null && ref instanceof PsiPolyVariantReference) {
          element = ref.getElement();
        }
      }

      final Lookup activeLookup = LookupManager.getInstance(myProject).getActiveLookup();

      if (activeLookup != null) {
        LookupItem item = activeLookup.getCurrentItem();
        if (item == null) return null;

        final DocumentationProvider documentationProvider = getProviderFromElement(file);

        if (documentationProvider!=null) {
          element = documentationProvider.getDocumentationElementForLookupItem(
            PsiManager.getInstance(myProject),
            item.getObject(),
            ref != null ? ref.getElement():contextElement
          );
        }
      }
    }

    storeOriginalElement(myProject, contextElement, element);

    return element;
  }

  private boolean fromQuickSearch() {
    return myPreviouslyFocused != null && myPreviouslyFocused.getParent() instanceof ChooseByNameBase.JPanelProvider;
  }

  private DocumentationCollector getDefaultCollector(final PsiElement _element) {
    return new DocumentationCollector() {
      private final SmartPsiElementPointer element = SmartPointerManager.getInstance(_element.getProject()).createSmartPsiElementPointer(_element);

      @Nullable
      public String getDocumentation() throws Exception {
        PsiElement element1 = element.getElement();
        final DocumentationProvider provider = getProviderFromElement(element1);
        if (provider != null && myParameterInfoController != null) {
          final Object[] objects = myParameterInfoController.getSelectedElements();

          if (objects.length > 0) {
            @NonNls StringBuffer sb = null;

            for(Object o:objects) {
              PsiElement parameter = null;
              if (o instanceof PsiElement) {
                parameter = (PsiElement)o;
              }

              if (parameter != null) {
                final SmartPsiElementPointer originalElement = parameter.getUserData(ORIGINAL_ELEMENT_KEY);
                final String str2 = provider.generateDoc(parameter, originalElement != null ? originalElement.getElement() : null);
                if (str2 == null) continue;
                if (sb == null) sb = new StringBuffer();
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

        final SmartPsiElementPointer originalElement = element1 != null ? element1.getUserData(ORIGINAL_ELEMENT_KEY) : null;
        return provider != null ? provider.generateDoc(element1, originalElement != null ? originalElement.getElement() : null) : null;
      }

      @Nullable
      public PsiElement getElement() {
        return element.getElement();
      }
    };
  }

  @Nullable
  public JBPopup getDocInfoHint() {
    if (myDocInfoHintRef == null) return null;
    JBPopup hint = myDocInfoHintRef.get();
    if (hint == null || !hint.isVisible()) {
      myDocInfoHintRef = null;
      return null;
    }
    return hint;
  }

  public void fetchDocInfo(final DocumentationCollector provider, final DocumentationComponent component) {
    doFetchDocInfo(component, provider, true);
  }

  public void fetchDocInfo(final PsiElement element, final DocumentationComponent component) {
    doFetchDocInfo(component, getDefaultCollector(element), true);
  }

  public void queueFetchDocInfo(final DocumentationCollector provider, final DocumentationComponent component) {
    doFetchDocInfo(component, provider, false);
  }

  public void queueFetchDocInfo(final PsiElement element, final DocumentationComponent component) {
    queueFetchDocInfo(getDefaultCollector(element), component);
  }

  private void doFetchDocInfo(final DocumentationComponent component, final DocumentationCollector provider, final boolean cancelRequests) {
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
        final Exception[] ex = new Exception[1];
        final String text = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
          @Nullable
          public String compute() {
            try {
              return provider.getDocumentation();
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

        final PsiElement element = ApplicationManager.getApplication().runReadAction(new Computable<PsiElement>() {
          @Nullable
          public PsiElement compute() {
            return provider.getElement();
          }
        });
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
            jbPopup.setCaption(getTitle(element));
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
  public static DocumentationProvider getProviderFromElement(final PsiElement element) {
    PsiElement originalElement = getOriginalElement(element);
    PsiFile containingFile = originalElement != null ? originalElement.getContainingFile() : element != null ? element.getContainingFile() : null;
    Set<DocumentationProvider> result = new LinkedHashSet<DocumentationProvider>();

    DocumentationProvider originalProvider = containingFile != null ? LanguageDocumentation.INSTANCE
      .forLanguage(containingFile.getLanguage()) : null;

    final Language elementLanguage = element != null ?element.getLanguage():null;
    DocumentationProvider elementProvider = element == null ? null : LanguageDocumentation.INSTANCE.forLanguage(elementLanguage);

    ContainerUtil.addIfNotNull(elementProvider, result);
    ContainerUtil.addIfNotNull(originalProvider, result);
    if (containingFile != null) {
      ContainerUtil.addIfNotNull(LanguageDocumentation.INSTANCE.forLanguage(containingFile.getViewProvider().getBaseLanguage()), result);
    }

    if (result.isEmpty()) return null;
    return new CompositeDocumentationProvider(result);
  }

  @Nullable
  public static PsiElement getOriginalElement(final PsiElement element) {
    SmartPsiElementPointer originalElementPointer = element!=null ? element.getUserData(ORIGINAL_ELEMENT_KEY):null;
    return originalElementPointer != null ? originalElementPointer.getElement() : null;
  }

  void navigateByLink(final DocumentationComponent component, String url) {
    component.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    final PsiElement psiElement = component.getElement();
    final PsiManager manager = PsiManager.getInstance(getProject(psiElement));
    if (url.startsWith(PSI_ELEMENT_PROTOCOL)) {
      final String refText = url.substring(PSI_ELEMENT_PROTOCOL.length());
      DocumentationProvider provider = getProviderFromElement(psiElement);
      if (provider!=null) {
        final PsiElement targetElement = provider.getDocumentationElementForLink(manager, refText, psiElement);
        if (targetElement != null) {
          fetchDocInfo(getDefaultCollector(targetElement), component);
        }
      }
    }
    else {
      final String docUrl = url;

      fetchDocInfo
        (new DocumentationCollector() {
          public String getDocumentation() throws Exception {
            if (docUrl.startsWith(DOC_ELEMENT_PROTOCOL)) {
              final DocumentationProvider provider = getProviderFromElement(psiElement);
              if (provider instanceof ExtensibleDocumentationProvider) {
                final ExtensibleDocumentationProvider documentationProvider = (ExtensibleDocumentationProvider)provider;
                final String text = documentationProvider
                  .getExternalDocumentation(docUrl.substring(DOC_ELEMENT_PROTOCOL.length()), getProject(psiElement));
                if (text != null) {
                  return text;
                }
                documentationProvider.openExternalDocumentation(psiElement, getOriginalElement(psiElement));
              }
            }
            return "";
          }

          public PsiElement getElement() {
            //String loc = getElementLocator(docUrl);
            //
            //if (loc != null) {
            //  PsiElement context = component.getElement();
            //  return JavaDocUtil.findReferenceTarget(context.getManager(), loc, context);
            //}

            return psiElement;
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

  public Project getProject(@Nullable final PsiElement element) {
    assert element == null || myProject == element.getProject();
    return myProject;
  }

  private static interface DocumentationCollector {
    @Nullable
    String getDocumentation() throws Exception;
    @Nullable
    PsiElement getElement();
  }
}