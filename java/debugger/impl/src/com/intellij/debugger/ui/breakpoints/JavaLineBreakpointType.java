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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * Base class for java line-connected exceptions (line, method, field)
 * @author egor
 */
public class JavaLineBreakpointType extends JavaLineBreakpointTypeBase<JavaLineBreakpointProperties>
                                    implements JavaBreakpointType<JavaLineBreakpointProperties> {
  public JavaLineBreakpointType() {
    super("java-line", DebuggerBundle.message("line.breakpoints.tab.title"));
  }

  protected JavaLineBreakpointType(@NonNls @NotNull String id, @Nls @NotNull String title) {
    super(id, title);
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

  @NotNull
  @Override
  public JavaLineBreakpointProperties createBreakpointProperties(@NotNull VirtualFile file, int line) {
    return new JavaLineBreakpointProperties();
  }

  @NotNull
  @Override
  public Breakpoint<JavaLineBreakpointProperties> createJavaBreakpoint(Project project, XBreakpoint breakpoint) {
    return new LineBreakpoint<>(project, breakpoint);
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

    List<JavaBreakpointVariant> res = new SmartList<>();
    res.add(new JavaBreakpointVariant(position)); //all

    if (!(startMethod instanceof PsiLambdaExpression)) {
      res.add(new ExactJavaBreakpointVariant(position, startMethod, -1)); // base method
    }

    int ordinal = 0;
    for (PsiLambdaExpression lambda : lambdas) { //lambdas
      PsiElement firstElem = DebuggerUtilsEx.getFirstElementOnTheLine(lambda, document, position.getLine());
      XSourcePositionImpl elementPosition = XSourcePositionImpl.createByElement(firstElem);
      if (elementPosition != null) {
        res.add(new ExactJavaBreakpointVariant(elementPosition, lambda, ordinal++));
      }
    }

    return res;
  }

  public boolean matchesPosition(@NotNull LineBreakpoint<?> breakpoint, @NotNull SourcePosition position) {
    JavaBreakpointProperties properties = breakpoint.getProperties();
    if (properties instanceof JavaLineBreakpointProperties) {
      if (!(breakpoint instanceof RunToCursorBreakpoint) && ((JavaLineBreakpointProperties)properties).getLambdaOrdinal() == null) return true;
      PsiElement containingMethod = getContainingMethod(breakpoint);
      if (containingMethod == null) return false;
      return DebuggerUtilsEx.inTheMethod(position, containingMethod);
    }
    return true;
  }

  @Nullable
  public PsiElement getContainingMethod(@NotNull LineBreakpoint<?> breakpoint) {
    SourcePosition position = breakpoint.getSourcePosition();
    if (position == null) return null;

    JavaBreakpointProperties properties = breakpoint.getProperties();
    if (properties instanceof JavaLineBreakpointProperties && !(breakpoint instanceof RunToCursorBreakpoint)) {
      Integer ordinal = ((JavaLineBreakpointProperties)properties).getLambdaOrdinal();
      if (ordinal > -1) {
        List<PsiLambdaExpression> lambdas = DebuggerUtilsEx.collectLambdas(position, true);
        if (ordinal < lambdas.size()) {
          return lambdas.get(ordinal);
        }
      }
    }
    return DebuggerUtilsEx.getContainingMethod(position);
  }

  public class JavaBreakpointVariant extends XLineBreakpointAllVariant {
    public JavaBreakpointVariant(@NotNull XSourcePosition position) {
      super(position);
    }
  }

  public class ExactJavaBreakpointVariant extends JavaBreakpointVariant {
    private final PsiElement myElement;
    private final Integer myLambdaOrdinal;

    public ExactJavaBreakpointVariant(@NotNull XSourcePosition position, PsiElement element, Integer lambdaOrdinal) {
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

    @NotNull
    @Override
    public JavaLineBreakpointProperties createProperties() {
      JavaLineBreakpointProperties properties = super.createProperties();
      assert properties != null;
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
          PsiElement method = getContainingMethod((LineBreakpoint)javaBreakpoint);
          if (method != null) {
            return method.getTextRange();
          }
        }
      }
    }
    return null;
  }

  @Override
  public boolean canBeHitInOtherPlaces() {
    return true; // line breakpoints could be hit in other versions of the same classes
  }
}
