/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.HelpID;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * Base class for java line-connected exceptions (line, method, field)
 * @author egor
 */
public class JavaLineBreakpointType extends JavaLineBreakpointTypeBase<JavaLineBreakpointProperties> implements JavaBreakpointType {
  public JavaLineBreakpointType() {
    super("java-line", DebuggerBundle.message("line.breakpoints.tab.title"));
  }

  //@Override
  protected String getHelpID() {
    return HelpID.LINE_BREAKPOINTS;
  }

  //@Override
  public String getDisplayName() {
    return DebuggerBundle.message("line.breakpoints.tab.title");
  }

  @Override
  public List<XBreakpointGroupingRule<XLineBreakpoint<JavaLineBreakpointProperties>, ?>> getGroupingRules() {
    return XDebuggerUtil.getInstance().getGroupingByFileRuleAsList();
  }

  @Nullable
  @Override
  public JavaLineBreakpointProperties createProperties() {
    return new JavaLineBreakpointProperties();
  }

  @Nullable
  @Override
  public JavaLineBreakpointProperties createBreakpointProperties(@NotNull VirtualFile file, int line) {
    return new JavaLineBreakpointProperties();
  }

  @NotNull
  @Override
  public Breakpoint createJavaBreakpoint(Project project, XBreakpoint breakpoint) {
    return new LineBreakpoint(project, breakpoint);
  }

  @Override
  public int getPriority() {
    return 100;
  }

  @NotNull
  @Override
  public List<JavaBreakpointVariant> computeVariants(@NotNull Project project, @NotNull XSourcePosition position) {
    PsiFile file = PsiManager.getInstance(project).findFile(position.getFile());
    if (file == null) {
      return Collections.emptyList();
    }

    SourcePosition pos = SourcePosition.createFromLine(file, position.getLine());
    List<PsiLambdaExpression> lambdas = DebuggerUtilsEx.collectLambdas(pos, true);
    if (lambdas.isEmpty()) {
      return Collections.emptyList();
    }

    PsiElement startMethod = DebuggerUtilsEx.getContainingMethod(pos);
    //noinspection SuspiciousMethodCalls
    if (lambdas.contains(startMethod) && lambdas.size() == 1) {
      return Collections.emptyList();
    }

    Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) {
      return Collections.emptyList();
    }

    List<JavaBreakpointVariant> res = new SmartList<JavaBreakpointVariant>();
    res.add(new JavaBreakpointVariant(position)); //all

    if (startMethod instanceof PsiMethod) {
      res.add(new ExactJavaBreakpointVariant(position, startMethod, -1)); // base method
    }

    int ordinal = 0;
    for (PsiLambdaExpression lambda : lambdas) { //lambdas
      PsiElement firstElem = DebuggerUtilsEx.getFirstElementOnTheLine(lambda, document, position.getLine());
      res.add(new ExactJavaBreakpointVariant(XSourcePositionImpl.createByElement(firstElem), lambda, ordinal++));
    }

    return res;
  }

  class JavaBreakpointVariant extends XLineBreakpointVariant {
    protected final XSourcePosition mySourcePosition;

    private JavaBreakpointVariant(XSourcePosition position) {
      mySourcePosition = position;
    }

    @Override
    public String getText() {
      return "All";
    }

    @Override
    public Icon getIcon() {
      return null;
    }

    @Override
    public TextRange getHighlightRange() {
      return null;
    }

    @Override
    public JavaLineBreakpointProperties createProperties() {
      return createBreakpointProperties(mySourcePosition.getFile(),
                                        mySourcePosition.getLine());
    }
  }

  private class ExactJavaBreakpointVariant extends JavaBreakpointVariant {
    private final PsiElement myElement;
    private final Integer myLambdaOrdinal;

    public ExactJavaBreakpointVariant(XSourcePosition position, PsiElement element, Integer lambdaOrdinal) {
      super(position);
      myElement = element;
      myLambdaOrdinal = lambdaOrdinal;
    }

    @Override
    public Icon getIcon() {
      return myElement.getIcon(0);
    }

    @Override
    public String getText() {
      return StringUtil.shortenTextWithEllipsis(myElement.getText(), 100, 0);
    }

    @Override
    public TextRange getHighlightRange() {
      return myElement.getTextRange();
    }

    @Override
    public JavaLineBreakpointProperties createProperties() {
      JavaLineBreakpointProperties properties = super.createProperties();
      properties.setLambdaOrdinal(myLambdaOrdinal);
      return properties;
    }
  }

  @Nullable
  @Override
  public TextRange getHighlightRange(XLineBreakpoint<JavaLineBreakpointProperties> breakpoint) {
    JavaLineBreakpointProperties properties = breakpoint.getProperties();
    if (properties != null) {
      Integer ordinal = properties.getLambdaOrdinal();
      if (ordinal != null) {
        Breakpoint javaBreakpoint = BreakpointManager.getJavaBreakpoint(breakpoint);
        if (javaBreakpoint instanceof LineBreakpoint) {
          PsiElement method = ((LineBreakpoint)javaBreakpoint).getContainingMethod();
          if (method != null) {
            return method.getTextRange();
          }
        }
      }
    }
    return null;
  }
}
