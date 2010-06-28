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
import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.StandardPatterns;
import com.intellij.patterns.compiler.PatternCompiler;
import com.intellij.patterns.compiler.PatternCompilerFactory;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PsiReferenceProviderBean extends CustomLoadingExtensionPointBean {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.PsiReferenceProviderBean");
  @Tag("className")
  public String className;
  @Tag("description")
  public String description;
  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false, elementTag = "patternClass", elementValueAttribute = "")
  public List<String> patternClasses = new ArrayList<String>();
  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false, elementTag = "pattern", elementValueAttribute = "")
  public List<String> patterns = new ArrayList<String>();

  public String getDescription() {
    return description;
  }

  public PsiReferenceProvider instantiate() {
    try {
      return (PsiReferenceProvider)instantiateExtension(className, ApplicationManager.getApplication().getPicoContainer());
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
    }
    return null;
  }

  @Nullable
  public ElementPattern<PsiElement> createElementPattern() {
    final ArrayList<Class> classes = new ArrayList<Class>();
    for (String patternClass : patternClasses) {
      try {
        classes.add(Class.forName(patternClass, true, getLoaderForClass()));
      }
      catch (ClassNotFoundException e) {
        LOG.error(e);
      }
    }
    final PatternCompiler<PsiElement> compiler =
      PatternCompilerFactory.getFactory().getPatternCompiler(classes.toArray(new Class[classes.size()]));
    if (patterns.size() > 1) {
      final ElementPattern[] patterns = new ElementPattern[this.patterns.size()];
      for (int i = 0, len = this.patterns.size(); i < len; i++) {
        patterns[i] = compiler.compileElementPattern(this.patterns.get(i));
      }
      return StandardPatterns.or(patterns);
    }
    else if (!patterns.isEmpty()) {
      return compiler.compileElementPattern(patterns.get(0));
    }
    else {
      LOG.error("At least one pattern should be specified");
      return null;
    }
  }
}
