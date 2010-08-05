/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.ParameterInfoController;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationHandler;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.content.*;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.NotLookupOrSearchCondition;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.util.Alarm;
import com.intellij.util.Processor;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DocumentationManager {
  private static final Logger LOG = Logger.getInstance("#" + DocumentationManager.class.getName());
  private static final String SHOW_DOCUMENTATION_IN_TOOL_WINDOW = "ShowDocumentationInToolWindow";
  private static final String DOCUMENTATION_AUTO_UPDATE_ENABLED = "DocumentationAutoUpdateEnabled";
  @NonNls public static final String JAVADOC_LOCATION_AND_SIZE = "javadoc.popup";
  private final Project myProject;
  private Editor myEditor = null;
  private ParameterInfoController myParameterInfoController;
  private final Alarm myUpdateDocAlarm;
  private WeakReference<JBPopup> myDocInfoHintRef;
  private Component myPreviouslyFocused = null;
  public static final Key<SmartPsiElementPointer> ORIGINAL_ELEMENT_KEY = Key.create("Original element");
  @NonNls public static final String PSI_ELEMENT_PROTOCOL = "psi_element://";
  @NonNls private static final String DOC_ELEMENT_PROTOCOL = "doc_element://";
  private ToolWindow myToolWindow = null;

  private final ActionManagerEx myActionManagerEx;

  private static final int ourFlagsForTargetElements = TargetElementUtilBase.getInstance().getAllAccepted();
  private boolean myAutoUpdateDocumentation = PropertiesComponent.getInstance().isTrueValue(DOCUMENTATION_AUTO_UPDATE_ENABLED);
  private Runnable myAutoUpdateRequest;

  public static DocumentationManager getInstance(Project project) {
    return ServiceManager.getService(project, DocumentationManager.class);
  }

  public DocumentationManager(Project project, ActionManagerEx managerEx) {
    myProject = project;
    myActionManagerEx = managerEx;
    final AnActionListener actionListener = new AnActionListener() {
      public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        final JBPopup hint = getDocInfoHint();
        if (hint != null) {
          if (action instanceof HintManagerImpl.ActionToIgnore) return;
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


      public void afterActionPerformed(final AnAction action, final DataContext dataContext, AnActionEvent event) {
      }
    };
    myActionManagerEx.addAnActionListener(actionListener, project);
    myUpdateDocAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD,myProject);
  }

  public void showJavaDocInfo(@NotNull final PsiElement element, final PsiElement original) {
    PopupUpdateProcessor updateProcessor = new PopupUpdateProcessor(element.getProject()) {
      public void updatePopup(Object lookupItemObject) {
        if (lookupItemObject instanceof PsiElement) {
          doShowJavaDocInfo((PsiElement)lookupItemObject, true, false, this, original, false);
        }
      }
    };

    doShowJavaDocInfo(element, true, false, updateProcessor, original, false);
  }

  public void showJavaDocInfo(final Editor editor, @Nullable final PsiFile file, boolean requestFocus) {
    showJavaDocInfo(editor, file, requestFocus, false);
  }

  private void showJavaDocInfo(final Editor editor, @Nullable final PsiFile file, boolean requestFocus, final boolean autoupdate) {
    myEditor = editor;
    final Project project = getProject(file);
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final PsiElement list =
      ParameterInfoController.findArgumentList(file, editor.getCaretModel().getOffset(), -1);
    if (list != null) {
      myParameterInfoController = ParameterInfoController.findControllerAtOffset(editor, list.getTextRange().getStartOffset());
    }

    final PsiElement originalElement = file != null ? file.findElementAt(editor.getCaretModel().getOffset()) : null;
    PsiElement element = findTargetElement(editor, file, originalElement);

    if (element == null && myParameterInfoController != null) {
      final Object[] objects = myParameterInfoController.getSelectedElements();

      if (objects != null && objects.length > 0) {
        if (objects[0] instanceof PsiElement) {
          element = (PsiElement)objects[0];
        }
      }
    }

    if (element == null && file == null) return; //file == null for text field editor

    if (element == null) { // look if we are within a javadoc comment
      element = originalElement;
      if (element == null) return;
      PsiComment comment = PsiTreeUtil.getParentOfType(element, PsiComment.class);
      if (comment == null) return;
      element = comment.getParent();
      //if (!(element instanceof PsiDocCommentOwner)) return null;
    }

    storeOriginalElement(project, originalElement, element);

    final PopupUpdateProcessor updateProcessor = new PopupUpdateProcessor(project) {
      public void updatePopup(Object lookupIteObject) {
        if (lookupIteObject instanceof PsiElement) {
          doShowJavaDocInfo((PsiElement)lookupIteObject, false, false, this, originalElement, autoupdate);
          return;
        }

        DocumentationProvider documentationProvider = getProviderFromElement(file);

        PsiElement element = null;
        if (documentationProvider!=null) {
          element = documentationProvider.getDocumentationElementForLookupItem(
            PsiManager.getInstance(myProject),
            lookupIteObject,
            originalElement
          );
        }

        if (element == null) return;

        if (myEditor != null) {
          final PsiFile file = element.getContainingFile();
          if (file != null) {
            Editor editor = myEditor;
            showJavaDocInfo(myEditor, file, false);
            myEditor = editor;
          }
        }
        else {
          doShowJavaDocInfo(element, false, false, this, originalElement, autoupdate);
        }
      }
    };

    doShowJavaDocInfo(element, false, requestFocus, updateProcessor, originalElement, autoupdate);
  }

  private void doShowJavaDocInfo(final PsiElement element, boolean heavyWeight, boolean requestFocus, PopupUpdateProcessor updateProcessor, final PsiElement originalElement, final boolean autoupdate) {
    Project project = getProject(element);

    if (myToolWindow == null && PropertiesComponent.getInstance().isTrueValue(SHOW_DOCUMENTATION_IN_TOOL_WINDOW)) {
      createToolWindow(element, originalElement, true);
      return;
    }
    else if (myToolWindow != null) {
      final Content content = myToolWindow.getContentManager().getSelectedContent();
      if (content != null) {
        final DocumentationComponent component = (DocumentationComponent)content.getComponent();
        if (component.getElement() != element) {
          content.setDisplayName(getTitle(element, true));
          fetchDocInfo(getDefaultCollector(element, originalElement), component, true);
          if (!myToolWindow.isVisible()) myToolWindow.show(null);
          return;
        } else {
          if (element != null && !autoupdate) {
            restorePopupBehavior();
          } else {
            return;
          }
        }
      }
    }

    final DocumentationComponent component = new DocumentationComponent(this);
    Processor<JBPopup> pinCallback = new Processor<JBPopup>() {
      public boolean process(JBPopup popup) {
        createToolWindow(element, originalElement, true);
        popup.cancel();
        return false;
      }
    };

    final KeyboardShortcut keyboardShortcut = ActionManagerEx.getInstanceEx().getKeyboardShortcut("QuickJavaDoc");
    final List<Pair<ActionListener, KeyStroke>> actions = Collections.singletonList(Pair.<ActionListener, KeyStroke>create(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          createToolWindow(element, originalElement, false);
          final JBPopup hint = getDocInfoHint();
          if (hint != null && hint.isVisible()) hint.cancel();
        }
      }, keyboardShortcut != null ? keyboardShortcut.getFirstKeyStroke() : null)); // Null keyStroke is ok here

      final JBPopup hint = JBPopupFactory.getInstance().createComponentPopupBuilder(component, component)
          .setRequestFocusCondition(project, NotLookupOrSearchCondition.INSTANCE)
          .setProject(project)
          .addListener(updateProcessor)
          .addUserData(updateProcessor)
          .setKeyboardActions(actions)
          .setForceHeavyweight(heavyWeight)
          .setDimensionServiceKey(myProject, JAVADOC_LOCATION_AND_SIZE, false)
          .setResizable(true)
          .setMovable(true)
          .setTitle(getTitle(element, false))
          .setCouldPin(pinCallback)
          .setCancelCallback(new Computable<Boolean>() {
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


      AbstractPopup oldHint = (AbstractPopup)getDocInfoHint();
      if (oldHint != null) {
        DocumentationComponent oldComponent = (DocumentationComponent)oldHint.getComponent();
        PsiElement element1 = oldComponent.getElement();
        if (Comparing.equal(element, element1)) {
          if (requestFocus) {
            component.getComponent().requestFocus();
          }
          return;
        }
        oldHint.cancel();
      }

      component.setHint(hint);

      if (myEditor == null) { 
        // subsequent invocation of javadoc popup from completion will have myEditor == null because of cancel invoked, 
        // so reevaluate the editor for proper popup placement
        Lookup lookup = LookupManager.getInstance(myProject).getActiveLookup();
        myEditor = lookup != null ? lookup.getEditor() : null;
      }
      fetchDocInfo(getDefaultCollector(element, originalElement), component);

      myDocInfoHintRef = new WeakReference<JBPopup>(hint);
      myPreviouslyFocused = WindowManagerEx.getInstanceEx().getFocusedComponent(project);

      if (fromQuickSearch()) {
        ((ChooseByNameBase.JPanelProvider)myPreviouslyFocused.getParent()).registerHint(hint);
      }
  }

  private void createToolWindow(final PsiElement element, PsiElement originalElement, final boolean automatic) {
    assert myToolWindow == null;

    final DocumentationComponent component = new DocumentationComponent(this, new AnAction[]{
      new ToggleAction("Auto show documentation for selected element", "Show documentation for current element automatically",
                          IconLoader.getIcon("/general/autoscrollFromSource.png")) {
        @Override
        public boolean isSelected(AnActionEvent e) {
          return myAutoUpdateDocumentation;
        }

        @Override
        public void setSelected(AnActionEvent e, boolean state) {
          PropertiesComponent.getInstance().setValue(DOCUMENTATION_AUTO_UPDATE_ENABLED, Boolean.TRUE.toString());
          myAutoUpdateDocumentation = state;
          restartAutoUpdate(state);
        }
      },
      new AnAction("Restore popup behavior", "Restore documentation popup behavior", IconLoader.getIcon("/actions/cancel.png")) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          restorePopupBehavior();
        }
      }});

    final ToolWindowManagerEx toolWindowManagerEx = ToolWindowManagerEx.getInstanceEx(myProject);
    myToolWindow = toolWindowManagerEx.registerToolWindow(ToolWindowId.DOCUMENTATION, true, ToolWindowAnchor.RIGHT, myProject);
    myToolWindow.setIcon(IconLoader.getIcon("/general/documentation.png"));

    myToolWindow.setAvailable(true, null);
    myToolWindow.setToHideOnEmptyContent(false);
    myToolWindow.setAutoHide(false);

    final Rectangle rectangle = WindowManager.getInstance().getIdeFrame(myProject).suggestChildFrameBounds();
    myToolWindow.setDefaultState(ToolWindowAnchor.RIGHT, ToolWindowType.FLOATING, rectangle);

    final ContentManager contentManager = myToolWindow.getContentManager();
    final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    final Content content = contentFactory.createContent(component, getTitle(element, true), false);
    contentManager.addContent(content);

    contentManager.addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void contentRemoved(ContentManagerEvent event) {
        if (contentManager.getContentCount() == 0) {
          final JComponent c = event.getContent().getComponent();
          if (c instanceof DocumentationComponent) {
            Disposer.dispose((DocumentationComponent) c);
          }
          
          restorePopupBehavior();
        }
      }
    });

    new UiNotifyConnector(component, new Activatable() {
      public void showNotify() {
        restartAutoUpdate(myAutoUpdateDocumentation);
      }

      public void hideNotify() {
        restartAutoUpdate(false);
      }
    });

    myToolWindow.show(null);
    PropertiesComponent.getInstance().setValue(SHOW_DOCUMENTATION_IN_TOOL_WINDOW, Boolean.TRUE.toString());
    restartAutoUpdate(PropertiesComponent.getInstance().isTrueValue(DOCUMENTATION_AUTO_UPDATE_ENABLED));
    fetchDocInfo(getDefaultCollector(element, originalElement), component);
  }

  private void restartAutoUpdate(final boolean state) {
    if (state && myToolWindow != null) {
      if (myAutoUpdateRequest == null) {
        myAutoUpdateRequest = new Runnable() {
          public void run() {
            final DataContext dataContext = DataManager.getInstance().getDataContext();
            final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
            if (editor != null) {
              final PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, myProject);

              final Editor injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file);
              if (injectedEditor != null) {
                final PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(injectedEditor, myProject);
                if (psiFile != null) {
                  showJavaDocInfo(injectedEditor, psiFile, false, true);
                  return;
                }
              }

              if (file != null) {
                showJavaDocInfo(editor, file, false, true);
              }
            }
          }
        };

        IdeEventQueue.getInstance().addIdleListener(myAutoUpdateRequest, 500);
      }
    } else {
      if (myAutoUpdateRequest != null) {
        IdeEventQueue.getInstance().removeIdleListener(myAutoUpdateRequest);
        myAutoUpdateRequest = null;
      }
    }
  }

  private void restorePopupBehavior() {
    if (myToolWindow != null) {
      PropertiesComponent.getInstance().setValue(SHOW_DOCUMENTATION_IN_TOOL_WINDOW, Boolean.FALSE.toString());

      final Content[] contents = myToolWindow.getContentManager().getContents();
      for (final Content content : contents) {
        final JComponent c = content.getComponent();
        if (c instanceof DocumentationComponent) {
          Disposer.dispose((DocumentationComponent) c);
        }
      }

      ToolWindowManagerEx.getInstanceEx(myProject).unregisterToolWindow(ToolWindowId.DOCUMENTATION);
      myToolWindow = null;
      restartAutoUpdate(false);
    }
  }

  private static String getTitle(@NotNull final PsiElement element, final boolean _short) {
    final String title = SymbolPresentationUtil.getSymbolPresentableText(element);
    return _short ? title != null ? title : element.getText() : CodeInsightBundle.message("javadoc.info.title", title != null ? title : element.getText());
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
      element = getElementFromLookup(editor, file);
      
      if (element == null) {
        final PsiReference ref = TargetElementUtilBase.findReference(editor, editor.getCaretModel().getOffset());

        if (ref != null) {
          element = TargetElementUtilBase.getInstance().adjustReference(ref);
          if (element == null && ref instanceof PsiPolyVariantReference) {
            element = ref.getElement();
          }
        }
      }
    }

    storeOriginalElement(myProject, contextElement, element);

    return element;
  }

  @Nullable
  public PsiElement getElementFromLookup(final Editor editor, @Nullable final PsiFile file) {

    final Lookup activeLookup = LookupManager.getInstance(myProject).getActiveLookup();

    if (activeLookup != null) {
      LookupElement item = activeLookup.getCurrentItem();
      if (item != null) {

        final PsiElement contextElement = file != null ? file.findElementAt(editor.getCaretModel().getOffset()) : null;
        final PsiReference ref = TargetElementUtilBase.findReference(editor, editor.getCaretModel().getOffset());

        final DocumentationProvider documentationProvider = getProviderFromElement(file);

        return documentationProvider.getDocumentationElementForLookupItem(
          PsiManager.getInstance(myProject),
          item.getObject(),
          ref != null ? ref.getElement():contextElement
        );
      }
    }
    return null;
  }

  private boolean fromQuickSearch() {
    return myPreviouslyFocused != null && myPreviouslyFocused.getParent() instanceof ChooseByNameBase.JPanelProvider;
  }

  private DocumentationCollector getDefaultCollector(@NotNull final PsiElement element, @Nullable final PsiElement originalElement) {
    return new DocumentationCollector() {

      @Nullable
      public String getDocumentation() throws Exception {
        final DocumentationProvider provider = ApplicationManager.getApplication().runReadAction(
            new Computable<DocumentationProvider>() {
              public DocumentationProvider compute() {
                return getProviderFromElement(element, originalElement);
              }
            }
        );
        if (myParameterInfoController != null) {
          final String doc = ApplicationManager.getApplication().runReadAction(
              new Computable<String>() {
                public String compute() {
                  return generateParameterInfoDocumentation(provider);
                }
              }
          );
          if (doc != null) return doc;
        }
        if (provider instanceof ExternalDocumentationProvider) {
          final List<String> urls = ApplicationManager.getApplication().runReadAction(
              new Computable<List<String>>() {
                public List<String> compute() {
                  final SmartPsiElementPointer originalElement = element.getUserData(ORIGINAL_ELEMENT_KEY);
                  return provider.getUrlFor(element, originalElement != null ? originalElement.getElement() : null);
                }
              }
          );
          if (urls != null) {
            final String doc = ((ExternalDocumentationProvider)provider).fetchExternalDocumentation(myProject, element, urls);
            if (doc != null) return doc;
          }
        }
        return ApplicationManager.getApplication().runReadAction(
            new Computable<String>() {
              @Nullable
              public String compute() {
                final SmartPsiElementPointer originalElement = element.getUserData(ORIGINAL_ELEMENT_KEY);
                return provider.generateDoc(element, originalElement != null ? originalElement.getElement() : null);
              }
            }
        );
      }

      private String generateParameterInfoDocumentation(DocumentationProvider provider) {
        final Object[] objects = myParameterInfoController.getSelectedElements();

        if (objects.length > 0) {
          @NonNls StringBuffer sb = null;

          for (Object o : objects) {
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
            }
            else {
              sb = null;
              break;
            }
          }

          if (sb != null) return sb.toString();
        }
        return null;
      }

      @Nullable
      public PsiElement getElement() {
        return element.isValid() ? element : null;
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
    doFetchDocInfo(component, provider, true, false);
  }

  public void fetchDocInfo(final DocumentationCollector provider, final DocumentationComponent component, final boolean clearHistory) {
    doFetchDocInfo(component, provider, true, clearHistory);
  }

  public void fetchDocInfo(final PsiElement element, final DocumentationComponent component) {
    doFetchDocInfo(component, getDefaultCollector(element, null), true, false);
  }

  public ActionCallback queueFetchDocInfo(final DocumentationCollector provider, final DocumentationComponent component, final boolean clearHistory) {
    return doFetchDocInfo(component, provider, false, clearHistory);
  }

  public ActionCallback queueFetchDocInfo(final PsiElement element, final DocumentationComponent component) {
    return queueFetchDocInfo(getDefaultCollector(element, null), component, false);
  }

  private ActionCallback doFetchDocInfo(final DocumentationComponent component, final DocumentationCollector provider, final boolean cancelRequests, final boolean clearHistory) {
    final ActionCallback callback = new ActionCallback();
    component.startWait();
    if (cancelRequests) {
      myUpdateDocAlarm.cancelAllRequests();
    }
    if (component.isEmpty()) {
      component.setText(CodeInsightBundle.message("javadoc.fetching.progress"), null, clearHistory);
    }

    myUpdateDocAlarm.addRequest(new Runnable() {
      public void run() {
        final Throwable[] ex = new Throwable[1];
        String text = null;
        try {
          text = provider.getDocumentation();
        }
        catch (Throwable e) {
          LOG.info(e);
          ex[0] = e;
        }

        if (ex[0] != null) {
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              String message = ex[0] instanceof IndexNotReadyException
                             ? "Documentation is not available until indices are built."
                             : CodeInsightBundle.message("javadoc.external.fetch.error.message", ex[0].getLocalizedMessage());
              component.setText(message, null, true);
              callback.setDone();
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
        if (element == null) {
          return;
        }
        final String documentationText = text;
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                PsiDocumentManager.getInstance(myProject).commitAllDocuments();
              }
            });

            if (!element.isValid()) {
              callback.setDone();
              return;
            }

            if (documentationText == null) {
              component.setText(CodeInsightBundle.message("no.documentation.found"), element, true);
            }
            else if (documentationText.length() == 0) {
              component.setText(component.getText(), element, true, clearHistory);
            }
            else {
              component.setData(element, documentationText, clearHistory);
            }

            final AbstractPopup jbPopup = (AbstractPopup)getDocInfoHint();
            if(jbPopup==null){
              callback.setDone();
              return;
            }
            jbPopup.setCaption(getTitle(element, false));
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
            callback.setDone();
          }
        });
      }
    }, 10);
    return callback;
  }

  @NotNull 
  public static DocumentationProvider getProviderFromElement(final PsiElement element) {
    return getProviderFromElement(element, null);
  }

  @NotNull
  public static DocumentationProvider getProviderFromElement(@Nullable PsiElement element, @Nullable PsiElement originalElement) {
    if (element != null && !element.isValid()) {
      element = null;
    }
    if (originalElement != null && !originalElement.isValid()) {
      originalElement = null;
    }

    if (originalElement == null) {
      originalElement = getOriginalElement(element);
    }

    PsiFile containingFile =
      originalElement != null ? originalElement.getContainingFile() : element != null ? element.getContainingFile() : null;
    Set<DocumentationProvider> result = new LinkedHashSet<DocumentationProvider>();

    final Language containingFileLanguage = containingFile != null ? containingFile.getLanguage() : null;
    DocumentationProvider originalProvider =
      containingFile != null ? LanguageDocumentation.INSTANCE.forLanguage(containingFileLanguage) : null;

    final Language elementLanguage = element != null ? element.getLanguage() : null;
    DocumentationProvider elementProvider =
      element == null || elementLanguage.is(containingFileLanguage) ? null : LanguageDocumentation.INSTANCE.forLanguage(elementLanguage);

    result.add(elementProvider);
    result.add(originalProvider);

    if (containingFile != null) {
      final Language baseLanguage = containingFile.getViewProvider().getBaseLanguage();
      if (!baseLanguage.is(containingFileLanguage)) {
        result.add(LanguageDocumentation.INSTANCE.forLanguage(baseLanguage));
      }
    }
    return CompositeDocumentationProvider.wrapProviders(result);
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
    if (url.startsWith("open")) {
      final PsiFile containingFile = psiElement.getContainingFile();
      if (containingFile != null) {
        final VirtualFile virtualFile = containingFile.getVirtualFile();
        final OrderEntry libraryEntry = LibraryUtil.findLibraryEntry(virtualFile, myProject);
        if (libraryEntry != null) {
          ProjectSettingsService.getInstance(myProject).openProjectLibrarySettings(new NamedLibraryElement(libraryEntry.getOwnerModule(), libraryEntry));
        }
      }
    } else if (url.startsWith(PSI_ELEMENT_PROTOCOL)) {
      final String refText = url.substring(PSI_ELEMENT_PROTOCOL.length());
      DocumentationProvider provider = getProviderFromElement(psiElement);
      final PsiElement targetElement = provider.getDocumentationElementForLink(manager, refText, psiElement);
      if (targetElement != null) {
        fetchDocInfo(getDefaultCollector(targetElement, null), component);
      }
    }
    else {
      final DocumentationProvider provider = getProviderFromElement(psiElement);
      boolean processed = false;
      if (provider instanceof CompositeDocumentationProvider) {
        for (DocumentationProvider documentationProvider : ((CompositeDocumentationProvider)provider).getProviders()) {
          if (documentationProvider instanceof ExternalDocumentationHandler && ((ExternalDocumentationHandler)documentationProvider).handleExternalLink(manager, url, psiElement)) {
            processed = true;
            break;
          }
        }
      }

      if (!processed) {
        final String docUrl = url;

        fetchDocInfo
          (new DocumentationCollector() {
            public String getDocumentation() throws Exception {
              if (docUrl.startsWith(DOC_ELEMENT_PROTOCOL)) {
                final List<String> urls = ApplicationManager.getApplication().runReadAction(
                  new Computable<List<String>>() {
                    public List<String> compute() {
                      final DocumentationProvider provider = getProviderFromElement(psiElement);
                      return provider.getUrlFor(psiElement, getOriginalElement(psiElement));
                    }
                  }
                );
                BrowserUtil.launchBrowser(urls != null && !urls.isEmpty() ? urls.get(0) : docUrl);
              }
              else {
                BrowserUtil.launchBrowser(docUrl);
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
    }

    component.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
  }

  void showHint(final JBPopup hint) {
    if (myEditor != null) {
      hint.showInBestPositionFor(myEditor);
    }
    else if (myPreviouslyFocused != null) {
      hint.showInBestPositionFor(DataManager.getInstance().getDataContext(myPreviouslyFocused));
    } else {
      hint.showInBestPositionFor(DataManager.getInstance().getDataContext());
    }
  }

  public void requestFocus() {
    if (fromQuickSearch()) {
      myPreviouslyFocused.getParent().requestFocus();
    }
  }

  public Project getProject(@Nullable final PsiElement element) {
    assert element == null || !element.isValid() || myProject == element.getProject();
    return myProject;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void createHyperlink(StringBuilder buffer, String refText,String label,boolean plainLink) {
    buffer.append("<a href=\"");
    buffer.append("psi_element://"); // :-)
    buffer.append(refText);
    buffer.append("\">");
    if (!plainLink) {
      buffer.append("<code>");
    }
    buffer.append(label);
    if (!plainLink) {
      buffer.append("</code>");
    }
    buffer.append("</a>");
  }

  private static interface DocumentationCollector {
    @Nullable
    String getDocumentation() throws Exception;
    @Nullable
    PsiElement getElement();
  }
}
