/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementMatcherSettings;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.psi.codeStyle.arrangement.sort.ArrangementEntrySortType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 7/20/12 2:31 PM
 */
public class JavaRearranger implements Rearranger<JavaElementArrangementEntry>, ArrangementStandardSettingsAware {

  // Type
  @NotNull private static final Set<ArrangementEntryType> SUPPORTED_TYPES = EnumSet.of(
    ArrangementEntryType.CLASS, ArrangementEntryType.FIELD, ArrangementEntryType.METHOD
  );

  // Modifier
  @NotNull private static final Set<ArrangementModifier>              SUPPORTED_MODIFIERS = EnumSet.of(
    ArrangementModifier.PUBLIC, ArrangementModifier.PROTECTED, ArrangementModifier.PACKAGE_PRIVATE, ArrangementModifier.PRIVATE,
    ArrangementModifier.STATIC, ArrangementModifier.FINAL, ArrangementModifier.VOLATILE, ArrangementModifier.TRANSIENT,
    ArrangementModifier.SYNCHRONIZED
  );
  @NotNull private static final Object                                NO_TYPE             = new Object();
  @NotNull private static final Map<Object, Set<ArrangementModifier>> MODIFIERS_BY_TYPE   = new HashMap<Object, Set<ArrangementModifier>>();

  static {
    EnumSet<ArrangementModifier> commonModifiers = EnumSet.of(
      ArrangementModifier.PUBLIC, ArrangementModifier.PROTECTED, ArrangementModifier.PACKAGE_PRIVATE, ArrangementModifier.PRIVATE,
      ArrangementModifier.STATIC, ArrangementModifier.FINAL
    );
    MODIFIERS_BY_TYPE.put(NO_TYPE, commonModifiers);
    MODIFIERS_BY_TYPE.put(ArrangementEntryType.CLASS, commonModifiers);
    MODIFIERS_BY_TYPE.put(ArrangementEntryType.METHOD, concat(commonModifiers, ArrangementModifier.SYNCHRONIZED));
    MODIFIERS_BY_TYPE.put(ArrangementEntryType.FIELD,
                          concat(commonModifiers, ArrangementModifier.TRANSIENT, ArrangementModifier.VOLATILE));
  }

  @NotNull
  private static Set<ArrangementModifier> concat(@NotNull Set<ArrangementModifier> base, ArrangementModifier... modifiers) {
    EnumSet<ArrangementModifier> result = EnumSet.copyOf(base);
    Collections.addAll(result, modifiers);
    return result;
  }

  @NotNull
  @Override
  public Collection<JavaElementArrangementEntry> parse(@NotNull PsiElement root,
                                                       @NotNull Document document,
                                                       @NotNull Collection<TextRange> ranges)
  {
    // Following entries are subject to arrangement: class, interface, field, method.
    List<JavaElementArrangementEntry> result = new ArrayList<JavaElementArrangementEntry>();
    root.accept(new JavaArrangementVisitor(result, document, ranges));
    return result;
  }

  @Override
  public boolean isNameFilterEnabled(@Nullable ArrangementMatcherSettings settings) {
    // TODO den implement 
    return true;
  }

  @Override
  public boolean isEnabled(@NotNull ArrangementEntryType type, @Nullable ArrangementMatcherSettings settings) {
    return SUPPORTED_TYPES.contains(type);
  }

  @Override
  public boolean isEnabled(@NotNull ArrangementModifier modifier, @Nullable ArrangementMatcherSettings settings) {
    if (settings == null) {
      return SUPPORTED_MODIFIERS.contains(modifier);
    }
    Object key = NO_TYPE;
    for (ArrangementEntryType type : ArrangementEntryType.values()) {
      if (settings.hasCondition(type)) {
        key = type;
        break;
      }
    }
    Set<ArrangementModifier> modifiers = MODIFIERS_BY_TYPE.get(key);
    return modifiers != null && modifiers.contains(modifier);
  }

  @Override
  public boolean isEnabled(@NotNull ArrangementEntrySortType type, @Nullable ArrangementMatcherSettings settings) {
    // TODO den implement 
    return true;
  }
}
