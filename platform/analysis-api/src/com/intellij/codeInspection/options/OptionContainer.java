// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.NotNull;

/**
 * A container that contains inspection options, which can be read or written, somewhat similar to a 'JavaBean'.
 * It's implemented by every inspection. If inspection has a complex nested object as an option, then
 * it could implement this interface as well. In this case, control bindId with prefix will be automatically
 * dispatched without necessity to create a custom {@link OptionController}.
 * <p>Here is a simple example of inspection that contains a tab with nested options. In this case, you need to implement
 * {@code OptionContainer} for the nested object and declare a prefix for the corresponding tab. The default 
 * {@code OptionController} will dispatch automatically to the field of the nested object:
 * <pre>{@code
 * class MyInspection extends InspectionProfileEntry {
 *   private MyComplexOption myOption = new MyComplexOption();
 *
 *   @Override
 *   public @NotNull OptPane getOptionsPane() {
 *     return pane(
 *       tabs(
 *         // Use .prefix("myOption") with the field name
 *         // to automatically dispatch options declared inside the tab 
 *         // to the nested container
 *         myOption.getPane().asTab("Tab").prefix("myOption")
 *       )
 *     );
 *   }
 *
 *   static class MyComplexOption implements OptionContainer {
 *     private boolean myFlag;
 *
 *     OptPane getPane() {
 *       return pane(checkbox("myFlag", "Flag"));
 *     }
 *   }
 * }
 * }</pre>
 * </p>
 */
public interface OptionContainer {
  /**
   * @return a controller to process options defined in this object.
   * Implemented by {@link com.intellij.codeInspection.InspectionProfileEntry}.
   * Could be also implemented by custom objects if inspection contains nested options,
   * and the default behavior is not desired.
   * <p>
   * @implNote The default implementation finds a field with the name that corresponds to bindId and uses/updates its value.
   * If the bindId is composite, then default implementation delegates to nested OptionContainers. 
   * <p>
   * If you need to process some options specially, you can override this method in particular inspection
   * and compose a new controller using methods like {@link OptionController#onPrefix} and
   * {@link OptionController#onValue}.
   * 
   * @see OptComponent#prefix(String)
   * @see OptionController#fieldsOf(Object)
   */
  default @NotNull OptionController getOptionController() {
    return OptionController.fieldsOf(this);
  }
}
