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
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.ide.DataManager;
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
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * @author lex
 */
public abstract class DebuggerEditorImpl extends CompletionEditor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.DebuggerEditorImpl");

  public static final char SEPARATOR = 13;

  private final Project myProject;
  private PsiElement myContext;

  private final String myRecentsId;

  private final List<DocumentListener> myDocumentListeners = new ArrayList<DocumentListener>();
  private Document myCurrentDocument;
  private final JLabel myChooseFactory = new JLabel();
  private WeakReference<ListPopup> myPopup;

  private final PsiTreeChangeListener myPsiListener = new PsiTreeChangeAdapter() {
    public void childRemoved(PsiTreeChangeEvent event) {
      checkContext();
    }
    public void childReplaced(PsiTreeChangeEvent event) {
      checkContext();
    }
    public void childMoved(PsiTreeChangeEvent event) {
      checkContext();
    }
    private void checkContext() {
      final PsiElement contextElement = getContext();
      if(contextElement == null || !contextElement.isValid()) {
        final DebuggerContextImpl context = DebuggerManagerEx.getInstanceEx(myProject).getContextManager().getContext();
        final PsiElement newContextElement = PositionUtil.getContextElement(context);
        setContext(newContextElement != null && newContextElement.isValid()? newContextElement : null);
      }
    }
  };
  private CodeFragmentFactory myFactory;
  protected boolean myInitialFactory;

  public DebuggerEditorImpl(Project project, PsiElement context, String recentsId, final CodeFragmentFactory factory) {
    myProject = project;
    myContext = context;
    myRecentsId = recentsId;
    PsiManager.getInstance(project).addPsiTreeChangeListener(myPsiListener);
    setFactory(factory);
    myInitialFactory = true;

    myChooseFactory.setToolTipText("Click to change the language");
    myChooseFactory.setBorder(new EmptyBorder(0, 3, 0, 3));
    myChooseFactory.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        ListPopup oldPopup = myPopup != null ? myPopup.get() : null;
        if (oldPopup != null) {
          oldPopup.cancel();
          myPopup = null;
          return;
        }

        DefaultActionGroup actions = new DefaultActionGroup();
        for (final CodeFragmentFactory fragmentFactory : getAllFactories()) {
          actions.add(new AnAction(fragmentFactory.getFileType().getLanguage().getDisplayName(), null, fragmentFactory.getFileType().getIcon()) {
            @Override
            public void actionPerformed(AnActionEvent e) {
              setFactory(fragmentFactory);
              setText(getText());
            }
          });
        }

        DataContext dataContext = DataManager.getInstance().getDataContext(DebuggerEditorImpl.this);
        ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup("Choose language", actions, dataContext,
                                                                              JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                                                              false);
        popup.showUnderneathOf(myChooseFactory);
        myPopup = new WeakReference<ListPopup>(popup);
      }
    });
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

  public void setContext(PsiElement context) {
    TextWithImports text = getText();
    myContext = context;

    List<CodeFragmentFactory> factories = getAllFactories();
    if (myInitialFactory || !factories.contains(myFactory)) {
      setFactory(factories.get(0));
    }
    myChooseFactory.setVisible(factories.size() > 1);
    myInitialFactory = false;

    setText(new TextWithImportsImpl(text.getKind(), text.getText(), text.getImports(), myFactory.getFileType()));
  }

  private List<CodeFragmentFactory> getAllFactories() {
    return DebuggerUtilsEx.getCodeFragmentFactories(myContext);
  }

  public PsiElement getContext() {
    return myContext;
  }

  protected Project getProject() {
    return myProject;
  }

  public void requestFocus() {
    getPreferredFocusedComponent().requestFocus();
  }

  @Nullable
  protected Document createDocument(TextWithImports item) {
    LOG.assertTrue(myContext == null || myContext.isValid());

    if(item == null) {
      item = createText("");
    }
    JavaCodeFragment codeFragment = getCurrentFactory().createPresentationCodeFragment(item, myContext, getProject());
    codeFragment.forceResolveScope(GlobalSearchScope.allScope(myProject));
    if (myContext != null) {
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
      for (DocumentListener documentListener : myDocumentListeners) {
        myCurrentDocument.addDocumentListener(documentListener);
      }
    }

    return myCurrentDocument;
  }

  public String getRecentsId() {
    return myRecentsId;
  }

  public void addRecent(TextWithImports text) {
    if(getRecentsId() != null && text != null && !"".equals(text.getText())){
      DebuggerRecents.getInstance(getProject()).addRecent(getRecentsId(), text);
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

  public void dispose() {
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiListener);
  }

  @NotNull
  public static CodeFragmentFactory findAppropriateFactory(@NotNull TextWithImports text, @NotNull PsiElement context) {
    for (CodeFragmentFactory factory : DebuggerUtilsEx.getCodeFragmentFactories(context)) {
      if (factory.getFileType().equals(text.getFileType())) {
        return factory;
      }
    }
    return DefaultCodeFragmentFactory.getInstance();
  }

  protected void restoreFactory(TextWithImports text) {
    FileType fileType = text.getFileType();
    if (fileType == null) return;
    if (myContext == null) return;

    setFactory(findAppropriateFactory(text, myContext));
  }

  private void setFactory(@NotNull final CodeFragmentFactory factory) {
    myFactory = factory;
    myChooseFactory.setIcon(getCurrentFactory().getFileType().getIcon());
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
