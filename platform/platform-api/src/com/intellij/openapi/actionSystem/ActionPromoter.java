/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.extensions.ExtensionPointName;

import java.util.List;

/**
 * It's allowed to assign multiple actions to the same keyboard shortcut. Actions system filters them on the current
 * context basis during processing (e.g. we can have two actions assigned to the same shortcut but one of them is
 * configured to be inapplicable in modal dialog context).
 * <p/>
 * However, there is a possible case that there is still more than one action applicable for particular keyboard shortcut
 * after filtering. The first one is executed then. Hence, actions processing order becomes very important.
 * <p/>
 * Current extension point allows to promote custom actions to use if any depending on data context
 *
 * @author Konstantin Bulenkov
 * @since 13
 */
public interface ActionPromoter {
  ExtensionPointName<ActionPromoter> EP_NAME = ExtensionPointName.create("com.intellij.actionPromoter");

  List<AnAction> promote(List<AnAction> actions, DataContext context);
}
