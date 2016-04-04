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
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.CodeFragmentFactoryContextWrapper;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.reference.SoftReference;
import com.intellij.ui.ClickListener;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.impl.XDebuggerHistoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * @author lex
 */
public abstract class DebuggerEditorImpl extends CompletionEditor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.DebuggerEditorImpl");

  public static final char SEPARATOR = 13;

  private final Project myProject;
  private PsiElement myContext;
  private PsiType myThisType;

  private final String myRecentsId;

  private final List<DocumentListener> myDocumentListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private Document myCurrentDocument;
  private final JLabel myChooseFactory = new JLabel();
  private WeakReference<ListPopup> myPopup;

  private CodeFragmentFactory myFactory;
  protected boolean myInitialFactory;

  public DebuggerEditorImpl(@NotNull Project project, @NotNull CodeFragmentFactory factory, @NotNull Disposable parentDisposable, @Nullable PsiElement context, @Nullable String recentsId) {
    myProject = project;
    myContext = context;
    myRecentsId = recentsId;
    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      @Override
      public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        checkContext();
      }
      @Override
      public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        checkContext();
      }
      @Override
      public void childMoved(@NotNull PsiTreeChangeEvent event) {
        checkContext();
      }
      private void checkContext() {
        PsiElement contextElement = getContext();
        if (contextElement == null || !contextElement.isValid()) {
          DebuggerManagerEx manager = DebuggerManagerEx.getInstanceEx(myProject);
          if (manager == null) {
            LOG.error("Cannot obtain debugger manager for project " + myProject);
            return;
          }

          PsiElement newContextElement = PositionUtil.getContextElement(manager.getContextManager().getContext());
          setContext(newContextElement != null && newContextElement.isValid() ? newContextElement : null);
        }
      }
    }, parentDisposable);
    setFactory(factory);
    myInitialFactory = true;

    setFocusable(false);

    myChooseFactory.setToolTipText("Click to change the language");
    myChooseFactory.setBorder(new EmptyBorder(0, 3, 0, 3));
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        ListPopup oldPopup = SoftReference.dereference(myPopup);
        if (oldPopup != null && !oldPopup.isDisposed()) {
          oldPopup.cancel();
          myPopup = null;
          return true;
        }

        if (myContext == null) {
          return true;
        }

        ListPopup popup = createLanguagePopup();
        popup.showUnderneathOf(myChooseFactory);
        myPopup = new WeakReference<>(popup);
        return true;
      }
    }.installOn(myChooseFactory);
  }

  private ListPopup createLanguagePopup() {
    DefaultActionGroup actions = new DefaultActionGroup();
    for (final CodeFragmentFactory fragmentFactory : DebuggerUtilsEx.getCodeFragmentFactories(myContext)) {
      actions.add(new AnAction(fragmentFactory.getFileType().getLanguage().getDisplayName(), null, fragmentFactory.getFileType().getIcon()) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          setFactory(fragmentFactory);
          setText(getText());
          IdeFocusManager.getInstance(getProject()).requestFocus(DebuggerEditorImpl.this, true);
        }
      });
    }

    DataContext dataContext = DataManager.getInstance().getDataContext(this);
    return JBPopupFactory.getInstance().createActionGroupPopup("Choose Language", actions, dataContext,
                                                               JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                                               false);
  }

  @Override
  public void setEnabled(boolean enabled) {
    myChooseFactory.setEnabled(enabled);
    super.setEnabled(enabled);
  }

  protected TextWithImports createItem(Document document, Project project) {
    if (document != null) {
      PsiDocumentManager.getInstance(project).commitDocument(document);
      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (psiFile != null) {
        return createText(psiFile.getText(), ((JavaCodeFragment)psiFile).importsToString());
      }
    }

    return createText("");
  }

  protected TextWithImports createText(String text) {
    return createText(text, "");
  }

  protected abstract TextWithImports createText(String text, String importsString);

  public abstract JComponent getPreferredFocusedComponent();

  @Override
  public void setContext(@Nullable PsiElement context) {
    myContext = context;

    List<CodeFragmentFactory> factories = DebuggerUtilsEx.getCodeFragmentFactories(context);
    boolean many = factories.size() > 1;
    if (myInitialFactory) {
      myInitialFactory = false;
      setFactory(factories.get(0));
      myChooseFactory.setVisible(many);
    }
    myChooseFactory.setVisible(myChooseFactory.isVisible() || many);
    myChooseFactory.setEnabled(many && factories.contains(myFactory));

    updateEditorUi();
  }

  @Override
  public void setText(TextWithImports text) {
    doSetText(text);
    updateEditorUi();
  }

  protected abstract void doSetText(TextWithImports text);

  protected abstract void updateEditorUi();

  @Override
  public PsiElement getContext() {
    return myContext;
  }

  protected Project getProject() {
    return myProject;
  }

  @Override
  public void requestFocus() {
    getPreferredFocusedComponent().requestFocus();
  }

  public void setThisType(PsiType thisType) {
    myThisType = thisType;
  }

  @Nullable
  protected Document createDocument(TextWithImports item) {
    LOG.assertTrue(myContext == null || myContext.isValid());

    if(item == null) {
      item = createText("");
    }
    JavaCodeFragment codeFragment = getCurrentFactory().createPresentationCodeFragment(item, myContext, getProject());
    codeFragment.forceResolveScope(GlobalSearchScope.allScope(myProject));
    if (myThisType != null) {
      codeFragment.setThisType(myThisType);
    }
    else if (myContext != null) {
      final PsiClass contextClass = PsiTreeUtil.getNonStrictParentOfType(myContext, PsiClass.class);
      if (contextClass != null) {
        final PsiClassType contextType = JavaPsiFacade.getInstance(codeFragment.getProject()).getElementFactory().createType(contextClass);
        codeFragment.setThisType(contextType);
      }
    }

    if(myCurrentDocument != null) {
      for (DocumentListener documentListener : myDocumentListeners) {
        myCurrentDocument.removeDocumentListener(documentListener);
      }
    }

    myCurrentDocument = PsiDocumentManager.getInstance(getProject()).getDocument(codeFragment);

    if (myCurrentDocument != null) {
      ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(getProject())).associatePsi(myCurrentDocument, codeFragment);
      for (DocumentListener documentListener : myDocumentListeners) {
        myCurrentDocument.addDocumentListener(documentListener);
      }
    }

    return myCurrentDocument;
  }

  @Override
  public String getRecentsId() {
    return myRecentsId;
  }

  public void addRecent(TextWithImports text) {
    if(getRecentsId() != null && text != null && !text.isEmpty()){
      XDebuggerHistoryManager.getInstance(getProject()).addRecentExpression(getRecentsId(), TextWithImportsImpl.toXExpression(text));
    }
  }

  public void addDocumentListener(DocumentListener listener) {
    myDocumentListeners.add(listener);
    if(myCurrentDocument != null) {
      myCurrentDocument.addDocumentListener(listener);
    }
  }

  public void removeDocumentListener(DocumentListener listener) {
    myDocumentListeners.remove(listener);
    if(myCurrentDocument != null) {
      myCurrentDocument.removeDocumentListener(listener);
    }
  }

  protected void restoreFactory(TextWithImports text) {
    FileType fileType = text.getFileType();
    if (fileType == null) return;
    if (myContext == null) return;

    setFactory(DebuggerUtilsEx.getCodeFragmentFactory(myContext, text.getFileType()));
  }

  private void setFactory(@NotNull CodeFragmentFactory factory) {
    myFactory = factory;
    Icon icon = getCurrentFactory().getFileType().getIcon();
    myChooseFactory.setIcon(icon);
    myChooseFactory.setDisabledIcon(IconLoader.getDisabledIcon(icon));
  }

  protected CodeFragmentFactory getCurrentFactory() {
    return myFactory instanceof CodeFragmentFactoryContextWrapper ? myFactory : new CodeFragmentFactoryContextWrapper(myFactory);
  }

  protected JPanel addChooseFactoryLabel(JComponent component, boolean top) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(component, BorderLayout.CENTER);

    JPanel factoryPanel = new JPanel(new BorderLayout());
    factoryPanel.add(myChooseFactory, top ? BorderLayout.NORTH : BorderLayout.CENTER);
    panel.add(factoryPanel, BorderLayout.WEST);
    return panel;
  }
}
