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

package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.StandardPatterns;
import com.intellij.patterns.compiler.PatternCompilerFactory;
import com.intellij.util.xmlb.annotations.*;
import org.jetbrains.annotations.Nullable;

public class PsiReferenceProviderBean extends AbstractExtensionPointBean {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.PsiReferenceProviderBean");
  @Attribute("providerClass")
  public String className;
  @Tag("description")
  public String description;

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public Info[] patterns;

  public String getDescription() {
    return description;
  }

  public PsiReferenceProvider instantiate() {
    try {
      return (PsiReferenceProvider)instantiate(className, ApplicationManager.getApplication().getPicoContainer());
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
    }
    return null;
  }

  @Nullable
  public ElementPattern<PsiElement> createElementPattern() {
    final PatternCompilerFactory factory = PatternCompilerFactory.getFactory();
    if (patterns.length > 1) {
      final ElementPattern[] result = new ElementPattern[this.patterns.length];
      for (int i = 0, len = this.patterns.length; i < len; i++) {
        result[i] = factory.getPatternCompiler(patterns[i].type).compileElementPattern(patterns[i].text);
      }
      return StandardPatterns.or(result);
    }
    else if (patterns.length == 1) {
      return factory.<PsiElement>getPatternCompiler(patterns[0].type).compileElementPattern(patterns[0].text);
    }
    else {
      LOG.error("At least one pattern should be specified");
      return null;
    }
  }

  @Tag("pattern")
  public static class Info {
    @Attribute("type")
    public String type;
    @Text
    public String text;
  }
}
