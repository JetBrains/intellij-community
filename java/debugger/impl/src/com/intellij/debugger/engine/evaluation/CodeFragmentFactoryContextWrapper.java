/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.ValueMarkup;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.util.StringBuilderSpinAllocator;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Aug 30, 2010
 */
public class CodeFragmentFactoryContextWrapper implements CodeFragmentFactory{
  public static final Key<Value> LABEL_VARIABLE_VALUE_KEY = Key.create("_label_variable_value_key_");
  public static final String DEBUG_LABEL_SUFFIX = "_DebugLabel";

  private final CodeFragmentFactory myDelegate;

  public CodeFragmentFactoryContextWrapper(CodeFragmentFactory delegate) {
    myDelegate = delegate;
  }

  public JavaCodeFragment createCodeFragment(TextWithImports item, PsiElement context, Project project) {
    return myDelegate.createCodeFragment(item, wrapContext(project, context), project);
  }

  public JavaCodeFragment createPresentationCodeFragment(TextWithImports item, PsiElement context, Project project) {
    return myDelegate.createPresentationCodeFragment(item, wrapContext(project, context), project);
  }

  public boolean isContextAccepted(PsiElement contextElement) {
    return myDelegate.isContextAccepted(contextElement);
  }

  public LanguageFileType getFileType() {
    return myDelegate.getFileType();
  }
  
  
  
  private PsiElement wrapContext(Project project, final PsiElement originalContext) {
    PsiElement context = originalContext;
    final DebugProcessImpl process = DebuggerManagerEx.getInstanceEx(project).getContext().getDebugProcess();
    if (process != null) {
      final Map<ObjectReference, ValueMarkup> markupMap = ValueDescriptorImpl.getMarkupMap(process);
      if (markupMap != null && markupMap.size() > 0) {
        final Pair<String, Map<String, ObjectReference>> markupVariables = createMarkupVariablesText(markupMap);
        int offset = markupVariables.getFirst().length() - 1;
        final TextWithImportsImpl textWithImports = new TextWithImportsImpl(CodeFragmentKind.CODE_BLOCK, markupVariables.getFirst(), "");
        final JavaCodeFragment codeFragment = myDelegate.createCodeFragment(textWithImports, context, project);
        codeFragment.accept(new JavaRecursiveElementVisitor() {
          public void visitLocalVariable(PsiLocalVariable variable) {
            final String name = variable.getName();
            variable.putUserData(LABEL_VARIABLE_VALUE_KEY, markupVariables.getSecond().get(name));
          }
        });
        final PsiElement newContext = codeFragment.findElementAt(offset);
        if (newContext != null) {
          context = newContext;
        }
      }
    }
    return context;
  }
  
  private static Pair<String, Map<String, ObjectReference>> createMarkupVariablesText(Map<ObjectReference, ValueMarkup> markupMap) {
    final Map<String, ObjectReference> reverseMap = new HashMap<String, ObjectReference>();
    final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      for (Iterator<Map.Entry<ObjectReference, ValueMarkup>> it = markupMap.entrySet().iterator(); it.hasNext();) {
        Map.Entry<ObjectReference, ValueMarkup> entry = it.next();
        final ObjectReference objectRef = entry.getKey();
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
          it.remove();
        }
      }
      buffer.append(" ");
      return new Pair<String, Map<String, ObjectReference>>(buffer.toString(), reverseMap);
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }
}
