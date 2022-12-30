// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.LiveTemplateContext;
import com.intellij.codeInsight.template.LiveTemplateContextService;
import com.intellij.codeInsight.template.LiveTemplateContextsSnapshot;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.util.JdomKt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.jvm.functions.Function0;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TemplateContext {
  private final Map<String, Boolean> myContextStates = new HashMap<>();

  public TemplateContext createCopy() {
    TemplateContext cloneResult = new TemplateContext();
    cloneResult.myContextStates.putAll(myContextStates);
    return cloneResult;
  }

  static boolean contextsEqual(@NotNull LiveTemplateContextsSnapshot allContexts,
                               @NotNull TemplateImpl thisTemplate,
                               @NotNull TemplateImpl defaultTemplate) {
    return getDifference(allContexts, thisTemplate.getTemplateContext(), defaultTemplate.getTemplateContext()) == null;
  }

  static @Nullable LiveTemplateContext getDifference(@NotNull LiveTemplateContextsSnapshot allContexts,
                                                     @NotNull TemplateContext thisContext,
                                                     @NotNull TemplateContext defaultContext) {
    return ContainerUtil.find(allContexts.getLiveTemplateContexts(),
                              type -> isEnabled(allContexts, thisContext, type) != isEnabled(allContexts, defaultContext, type));
  }

  static @Nullable TemplateContextType getDifferenceType(@NotNull LiveTemplateContextsSnapshot allContexts,
                                                         @NotNull TemplateContext thisContext,
                                                         @NotNull TemplateContext defaultContext) {
    LiveTemplateContext differenceExtension = getDifference(allContexts, thisContext, defaultContext);
    if (differenceExtension != null) {
      return differenceExtension.getTemplateContextType();
    }
    return null;
  }

  public boolean isEnabled(@NotNull TemplateContextType contextType) {
    LiveTemplateContextsSnapshot allContexts = LiveTemplateContextService.getInstance().getSnapshot();
    synchronized (myContextStates) {
      return isEnabledNoSync(this, allContexts, contextType.getContextId());
    }
  }

  private static boolean isEnabled(@NotNull LiveTemplateContextsSnapshot allContexts,
                                   @NotNull TemplateContext thisContext,
                                   @NotNull LiveTemplateContext contextType) {
    synchronized (thisContext.myContextStates) {
      return isEnabledNoSync(thisContext, allContexts, contextType.getContextId());
    }
  }

  private static boolean isEnabledNoSync(@NotNull TemplateContext thisContext,
                                         @NotNull LiveTemplateContextsSnapshot allContexts,
                                         @NotNull String contextTypeId) {
    Boolean storedValue = thisContext.getOwnValueNoSync(contextTypeId);
    if (storedValue == null) {
      LiveTemplateContext liveTemplateContext = allContexts.getLiveTemplateContext(contextTypeId);
      String baseContextTypeId = liveTemplateContext != null ? liveTemplateContext.getBaseContextId() : null;
      return baseContextTypeId != null && isEnabledNoSync(thisContext, allContexts, baseContextTypeId);
    }
    return storedValue.booleanValue();
  }

  public @Nullable Boolean getOwnValue(@NotNull TemplateContextType contextType) {
    return getOwnValue(contextType.getContextId());
  }

  private @Nullable Boolean getOwnValue(String contextTypeId) {
    synchronized (myContextStates) {
      return getOwnValueNoSync(contextTypeId);
    }
  }

  private @Nullable Boolean getOwnValueNoSync(String contextTypeId) {
    return myContextStates.get(contextTypeId);
  }

  public void setEnabled(TemplateContextType contextType, boolean value) {
    synchronized (myContextStates) {
      myContextStates.put(contextType.getContextId(), value);
    }
  }

  // used during initialization => no sync
  @VisibleForTesting
  public void setDefaultContext(@NotNull TemplateContext defContext) {
    Map<String, Boolean> copy = new HashMap<>(myContextStates);
    myContextStates.clear();
    myContextStates.putAll(defContext.myContextStates);
    myContextStates.putAll(copy);
  }

  // used during initialization => no sync
  @ApiStatus.Internal
  public void readTemplateContext(@NotNull Element element, @NotNull LiveTemplateContextService ltContextService) {
    Map<String, String> internMap = ltContextService.getInternalIds();
    for (Element option : element.getChildren("option")) {
      String name = option.getAttributeValue("name");
      String value = option.getAttributeValue("value");
      if (name != null && value != null) {
        myContextStates.put(ContainerUtil.getOrElse(internMap, name, name), Boolean.parseBoolean(value));
      }
    }

    myContextStates.putAll(makeInheritanceExplicit(this, ltContextService.getSnapshot()));
  }

  @VisibleForTesting
  public void readTemplateContext(@NotNull Element element) {
    readTemplateContext(element, LiveTemplateContextService.getInstance());
  }

  /**
   * Mark contexts explicitly as excluded which are excluded because some of their bases is explicitly marked as excluded.
   * Otherwise, that `excluded` status will be forgotten if the base context is enabled.
   */
  private @NotNull Map<String, Boolean> makeInheritanceExplicit(TemplateContext context, LiveTemplateContextsSnapshot allContexts) {
    Map<String, Boolean> explicitStates = new HashMap<>();
    for (LiveTemplateContext type : allContexts.getLiveTemplateContexts()) {
      if (isDisabledByInheritance(context, allContexts, type)) {
        explicitStates.put(type.getContextId(), false);
      }
    }
    return explicitStates;
  }

  private boolean isDisabledByInheritance(TemplateContext thisContext, LiveTemplateContextsSnapshot allContexts, LiveTemplateContext type) {
    return !thisContext.hasOwnValue(type) &&
           !isEnabled(allContexts, thisContext, type) &&
           JBIterable.generate(type, context -> allContexts.getLiveTemplateContext(context.getBaseContextId())).filter(this::hasOwnValue)
             .first() != null;
  }

  private boolean hasOwnValue(LiveTemplateContext t) {
    return getOwnValue(t.getContextId()) != null;
  }

  @TestOnly
  public Element writeTemplateContext(@Nullable TemplateContext defaultContext) {
    return writeTemplateContext(defaultContext, getIdToType());
  }

  @VisibleForTesting
  @Nullable
  public Element writeTemplateContext(@Nullable TemplateContext defaultContext,
                                      @NotNull Lazy<? extends Map<String, TemplateContextType>> idToType) {
    if (myContextStates.isEmpty()) {
      return null;
    }

    Element element = new Element(TemplateConstants.CONTEXT);
    List<Map.Entry<String, Boolean>> entries = new ArrayList<>(myContextStates.entrySet());
    entries.sort(Map.Entry.comparingByKey());
    for (Map.Entry<String, Boolean> entry : entries) {
      Boolean ownValue = entry.getValue();
      if (ownValue == null) {
        continue;
      }

      TemplateContextType type = idToType.getValue().get(entry.getKey());
      if (type == null) {
        // https://youtrack.jetbrains.com/issue/IDEA-155623#comment=27-1721029
        JdomKt.addOptionTag(element, entry.getKey(), ownValue.toString());
      }
      else if (isValueChanged(ownValue, type, defaultContext)) {
        JdomKt.addOptionTag(element, type.getContextId(), ownValue.toString());
      }
    }
    return element;
  }

  @NotNull
  public static Lazy<Map<String, TemplateContextType>> getIdToType() {
    return LazyKt.lazy(new Function0<>() {
      @Override
      public Map<String, TemplateContextType> invoke() {
        Map<String, TemplateContextType> idToType = new HashMap<>();
        for (LiveTemplateContext type : LiveTemplateContextService.getInstance().getLiveTemplateContexts()) {
          idToType.put(type.getContextId(), type.getTemplateContextType());
        }
        return idToType;
      }
    });
  }

  /**
   * Default value for GROOVY_STATEMENT is `true` (defined in the `plugins/groovy/groovy-psi/resources/liveTemplates/Groovy.xml`).
   * Base value is `false`.
   * <p>
   * If default value is defined (as in our example)  we must not take base value in account.
   * Because on init `setDefaultContext` will be called, and we will have own value.
   * Otherwise, it will be not possible to set value for `GROOVY_STATEMENT` neither to `true` (equals to default), nor to `false` (equals to base).
   * See TemplateSchemeTest.
   */
  private boolean isValueChanged(@NotNull Boolean ownValue, @NotNull TemplateContextType type, @Nullable TemplateContext defaultContext) {
    Boolean defaultValue = defaultContext == null ? null : defaultContext.getOwnValue(type.getContextId());
    if (defaultValue == null) {
      TemplateContextType base = type.getBaseContextType();
      boolean baseEnabled = base != null && isEnabled(base);
      return ownValue != baseEnabled;
    }
    return !ownValue.equals(defaultValue);
  }

  @Override
  public String toString() {
    return myContextStates.toString();
  }
}
