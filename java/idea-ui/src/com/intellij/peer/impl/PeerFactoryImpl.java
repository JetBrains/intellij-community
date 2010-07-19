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
package com.intellij.peer.impl;

import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileChooser.FileSystemTreeFactory;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.ui.configuration.JavaContentEntriesEditor;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.ui.DialogWrapperPeerFactory;
import com.intellij.openapi.ui.PackageChooser;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.vcs.FileStatusFactory;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.packageSet.PackageSetFactory;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.ui.*;
import com.intellij.ui.TextComponent;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.errorView.ErrorViewFactory;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.Function;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.Table;
import com.intellij.util.ui.UIUtil;
import org.apache.xmlrpc.IdeaAwareWebServer;
import org.apache.xmlrpc.IdeaAwareXmlRpcServer;
import org.apache.xmlrpc.WebServer;
import org.apache.xmlrpc.XmlRpcServer;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.net.InetAddress;

public class PeerFactoryImpl extends PeerFactory {
  private final UIHelper myUIHelper = new MyUIHelper();

  public FileStatusFactory getFileStatusFactory() {
    return ServiceManager.getService(FileStatusFactory.class);
  }

  public DialogWrapperPeerFactory getDialogWrapperPeerFactory() {
    return DialogWrapperPeerFactory.getInstance();
  }

  public PackageSetFactory getPackageSetFactory() {
    return PackageSetFactory.getInstance();
  }

  public UIHelper getUIHelper() {
    return myUIHelper;
  }

  public ErrorViewFactory getErrorViewFactory() {
    return ErrorViewFactory.SERVICE.getInstance();
  }

  public ContentFactory getContentFactory() {
    return ServiceManager.getService(ContentFactory.class);
  }

  public FileSystemTreeFactory getFileSystemTreeFactory() {
    return FileSystemTreeFactory.SERVICE.getInstance();
  }

  public DiffRequestFactory getDiffRequestFactory() {
    return DiffRequestFactory.getInstance();
  }

  private static class MyUIHelper implements UIHelper {
    public void installToolTipHandler(JTree tree) {
      TreeUIHelper.getInstance().installToolTipHandler(tree);
    }

    public void installToolTipHandler(JTable table) {
      TreeUIHelper.getInstance().installToolTipHandler(table);
    }

    public void installEditSourceOnDoubleClick(JTree tree) {
      EditSourceOnDoubleClickHandler.install(tree);
    }

    public void installEditSourceOnDoubleClick(TreeTable tree) {
      EditSourceOnDoubleClickHandler.install(tree);
    }

    public void installEditSourceOnDoubleClick(Table table) {
      EditSourceOnDoubleClickHandler.install(table);
    }

    public void installTreeTableSpeedSearch(TreeTable treeTable) {
      new TreeTableSpeedSearch(treeTable);
    }

    public void installTreeTableSpeedSearch(final TreeTable treeTable, final Convertor<TreePath, String> convertor) {
      new TreeTableSpeedSearch(treeTable, convertor);
    }

    public void installTreeSpeedSearch(JTree tree) {
      new TreeSpeedSearch(tree);
    }

    public void installTreeSpeedSearch(final JTree tree, final Convertor<TreePath, String> convertor) {
      new TreeSpeedSearch(tree, convertor);
    }

    public void installListSpeedSearch(JList list) {
      new ListSpeedSearch(list);
    }

    public void installListSpeedSearch(final JList list, final Function<Object, String> elementTextDelegate) {
      new ListSpeedSearch(list, elementTextDelegate);
    }

    public void installEditSourceOnEnterKeyHandler(JTree tree) {
      EditSourceOnEnterKeyHandler.install(tree);
    }

    public SplitterProportionsData createSplitterProportionsData() {
      return new SplitterProportionsDataImpl();
    }

    public TableCellRenderer createPsiElementRenderer(final PsiElement psiElement, final Project project) {
      return new ColoredTableCellRenderer() {
        protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
          append(getPsiElementText(psiElement), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          setIcon(psiElement.getIcon(0));
        }
      };

    }

    public TreeCellRenderer createHighlightableTreeCellRenderer() {
      return new HighlightableCellRenderer();
    }

    public void drawDottedRectangle(Graphics g, int x, int y, int i, int i1) {
      UIUtil.drawDottedRectangle(g,x,y,i,i1);
    }

    public void installSmartExpander(JTree tree) {
      SmartExpander.installOn(tree);
    }

    public void installSelectionSaver(JTree tree) {
      SelectionSaver.installOn(tree);
    }

    public TextComponent createTypedTextField(final String text, PsiType type, PsiElement context, final Project project) {
      final PsiExpressionCodeFragment fragment =
        JavaPsiFacade.getInstance(project).getElementFactory().createExpressionCodeFragment(text, context, type, true);
      final Document document = PsiDocumentManager.getInstance(project).getDocument(fragment);
      return new EditorTextField(document, project, StdFileTypes.JAVA);
    }

    public PackageChooser createPackageChooser(String title, Project project) {
      return new PackageChooserDialog(title, project);
    }

    private static String getPsiElementText(PsiElement psiElement) {
      if (psiElement instanceof PsiClass) {
        return PsiFormatUtil.formatClass((PsiClass)psiElement, PsiFormatUtil.SHOW_NAME |
                                                               PsiFormatUtil.SHOW_FQ_NAME);
      }
      else if (psiElement instanceof PsiMethod) {
        return PsiFormatUtil.formatMethod((PsiMethod)psiElement,
                                          PsiSubstitutor.EMPTY,
                                          PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS | PsiFormatUtil.SHOW_CONTAINING_CLASS,
                                          0);
      }
      else if (psiElement instanceof PsiField) {
        return PsiFormatUtil.formatVariable((PsiField)psiElement,
                                            PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_CONTAINING_CLASS,
                                            PsiSubstitutor.EMPTY);
      }
      else {
        return psiElement.toString();
      }

    }

  }

  public VcsContextFactory getVcsContextFactory() {
    return VcsContextFactory.SERVICE.getInstance();
  }

  public PsiBuilder createBuilder(ASTNode tree, Language lang, CharSequence seq, final Project project) {
    return PsiBuilderFactory.getInstance().createBuilder(project, tree, lang, seq);
  }

  public PsiBuilder createBuilder(final ASTNode tree, final Lexer lexer, final Language lang, final CharSequence seq, final Project project) {
    return PsiBuilderFactory.getInstance().createBuilder(project, tree, lexer, lang, seq);
  }

  public XmlRpcServer createRpcServer() {
    return new IdeaAwareXmlRpcServer();
  }

  public WebServer createWebServer(final int port, final InetAddress addr, final XmlRpcServer xmlrpc) {
    return new IdeaAwareWebServer(port, addr, xmlrpc);
  }

  public EditorHighlighter createEditorHighlighter(final SyntaxHighlighter syntaxHighlighter, final EditorColorsScheme colors) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(syntaxHighlighter, colors);
  }

  public Sdk createProjectJdk(final String name, final String version, final String homePath, final SdkType sdkType) {
    final ProjectJdkImpl projectJdk = new ProjectJdkImpl(name, sdkType);
    projectJdk.setHomePath(homePath);
    projectJdk.setVersionString(version);
    return projectJdk;
  }

  public ModuleConfigurationEditor createModuleConfigurationEditor(final String moduleName, ModuleConfigurationState state) {
    return new JavaContentEntriesEditor(moduleName, state);
  }

}