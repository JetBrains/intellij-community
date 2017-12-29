/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 */
public class CodeFragmentFactoryContextWrapper extends CodeFragmentFactory {
  public static final Key<Value> LABEL_VARIABLE_VALUE_KEY = Key.create("_label_variable_value_key_");
  public static final String DEBUG_LABEL_SUFFIX = "_DebugLabel";

  private final CodeFragmentFactory myDelegate;

  public CodeFragmentFactoryContextWrapper(CodeFragmentFactory delegate) {
    myDelegate = delegate;
  }

  public JavaCodeFragment createCodeFragment(TextWithImports item, PsiElement context, Project project) {
    return prepareResolveScope(myDelegate.createCodeFragment(item, wrapContext(project, context), project));
  }

  public JavaCodeFragment createPresentationCodeFragment(TextWithImports item, PsiElement context, Project project) {
    return prepareResolveScope(myDelegate.createPresentationCodeFragment(item, wrapContext(project, context), project));
  }

  public boolean isContextAccepted(PsiElement contextElement) {
    return myDelegate.isContextAccepted(contextElement);
  }

  @NotNull
  public LanguageFileType getFileType() {
    return myDelegate.getFileType();
  }

  @Override
  public EvaluatorBuilder getEvaluatorBuilder() {
    return myDelegate.getEvaluatorBuilder();
  }

  private static JavaCodeFragment prepareResolveScope(JavaCodeFragment codeFragment) {
    GlobalSearchScope originalResolveScope = codeFragment.getResolveScope();
    codeFragment.forceResolveScope(new DelegatingGlobalSearchScope(GlobalSearchScope.allScope(codeFragment.getProject())) {
      final Comparator<VirtualFile> myScopeComparator = Comparator.comparing(originalResolveScope::contains).thenComparing(super::compare);
      @Override
      public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
        // prefer files from the original resolve scope
        return myScopeComparator.compare(file1, file2);
      }
    });
    return codeFragment;
  }
  
  private PsiElement wrapContext(Project project, final PsiElement originalContext) {
    if (project.isDefault()) return originalContext;
    //TODO [egor] : does not work for anything other than java anyway, see IDEA-132677
    if (!(myDelegate instanceof DefaultCodeFragmentFactory)) {
      return originalContext;
    }
    PsiElement context = originalContext;
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session != null) {
      XValueMarkers<?, ?> markers = ((XDebugSessionImpl)session).getValueMarkers();
      Map<?, ValueMarkup> markupMap = markers != null ? markers.getAllMarkers() : null;
      if (!ContainerUtil.isEmpty(markupMap)) {
        Pair<String, Map<String, ObjectReference>> markupVariables = createMarkupVariablesText(markupMap);
        String text = markupVariables.getFirst();
        if (!StringUtil.isEmpty(text)) {
          PsiCodeBlock codeFragment =
            JavaPsiFacade.getInstance(project).getElementFactory().createCodeBlockFromText("{" + text + "}", context);
          codeFragment.accept(new JavaRecursiveElementVisitor() {
            public void visitLocalVariable(PsiLocalVariable variable) {
              final String name = variable.getName();
              variable.putUserData(LABEL_VARIABLE_VALUE_KEY, markupVariables.getSecond().get(name));
            }
          });
          context = codeFragment;
        }
      }
    }
    return context;
  }
  
  private static Pair<String, Map<String, ObjectReference>> createMarkupVariablesText(Map<?, ValueMarkup> markupMap) {
    final Map<String, ObjectReference> reverseMap = new HashMap<>();
    final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      for (Map.Entry<?, ValueMarkup> entry : markupMap.entrySet()) {
        ObjectReference objectRef = (ObjectReference)entry.getKey();
        final ValueMarkup markup = entry.getValue();
        String labelName = markup.getText();
        if (!StringUtil.isJavaIdentifier(labelName)) {
          continue;
        }
        try {
          final String typeName = objectRef.type().name();
          labelName += DEBUG_LABEL_SUFFIX;
          if (buffer.length() > 0) {
            buffer.append("\n");
          }
          buffer.append(typeName).append(" ").append(labelName).append(";");
          reverseMap.put(labelName, objectRef);
        }
        catch (ObjectCollectedException e) {
          //it.remove();
        }
      }
      return Pair.create(buffer.toString(), reverseMap);
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }
}
