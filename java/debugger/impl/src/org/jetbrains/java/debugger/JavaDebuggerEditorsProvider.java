package org.jetbrains.java.debugger;

import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.ui.DebuggerExpressionComboBox;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProviderBase;
import com.intellij.xdebugger.impl.breakpoints.ui.XDebuggerComboBoxProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JavaDebuggerEditorsProvider extends XDebuggerEditorsProviderBase implements XDebuggerComboBoxProvider {
  @NotNull
  @Override
  public FileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Override
  protected PsiFile createExpressionCodeFragment(@NotNull Project project, @NotNull String text, @Nullable PsiElement context, boolean isPhysical) {
    return JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment(text, context, null, isPhysical);
  }

  @Override
  public XBreakpointCustomPropertiesPanel<XBreakpoint<?>> createConditionComboBoxPanel(Project project,
                                                                                       XDebuggerEditorsProvider debuggerEditorsProvider,
                                                                                       String historyId,
                                                                                       XSourcePosition sourcePosition) {
    return new ExpressionComboBoxPanel(project, historyId, sourcePosition) {
      @Override
      public void saveTo(@NotNull XBreakpoint<?> breakpoint) {
        TextWithImports text = myComboBox.getText();
        final String condition = !text.getText().isEmpty() ? text.toExternalForm() : null;
        breakpoint.setCondition(condition);
        if (condition != null) {
          myComboBox.addRecent(text);
        }
      }

      @Override
      public void loadFrom(@NotNull XBreakpoint<?> breakpoint) {
        myComboBox.setText(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, StringUtil.notNullize(breakpoint.getCondition())));
      }
    };
  }

  @Override
  public XBreakpointCustomPropertiesPanel<XBreakpoint<?>> createLogExpressionComboBoxPanel(Project project,
                                                                                           XDebuggerEditorsProvider debuggerEditorsProvider,
                                                                                           String historyId,
                                                                                           XSourcePosition sourcePosition) {
    return new ExpressionComboBoxPanel(project, historyId, sourcePosition) {
      @Override
      public void saveTo(@NotNull XBreakpoint<?> breakpoint) {
        TextWithImports text = myComboBox.getText();
        breakpoint.setLogExpression(myComboBox.isEnabled() && !text.getText().isEmpty() ? text.toExternalForm() : null);
        if (text != null) {
          myComboBox.addRecent(text);
        }
      }

      @Override
      public void loadFrom(@NotNull XBreakpoint<?> breakpoint) {
        myComboBox.setText(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, StringUtil.notNullize(breakpoint.getLogExpression())));
      }
    };
  }

  private abstract class ExpressionComboBoxPanel extends XBreakpointCustomPropertiesPanel<XBreakpoint<?>> {
    protected final DebuggerExpressionComboBox myComboBox;

    private ExpressionComboBoxPanel(Project project,
                                    String historyId,
                                    XSourcePosition sourcePosition) {
      myComboBox = new DebuggerExpressionComboBox(project, historyId);
      if (sourcePosition != null) {
        PsiElement element = getContextElement(sourcePosition.getFile(), sourcePosition.getOffset(), project);
        myComboBox.setContext(element);
      }
      else {
        myComboBox.setContext(null);
      }
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return myComboBox;
    }

    @Override
    public void dispose() {
      myComboBox.dispose();
    }
  }
}