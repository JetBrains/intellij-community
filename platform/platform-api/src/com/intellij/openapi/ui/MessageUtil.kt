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
@file:JvmName("MessageUtil")
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
package com.intellij.openapi.ui

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import javax.swing.Icon

fun showYesNoDialog(@Nls(capitalization = Nls.Capitalization.Title) title: String, message: String, project: Project?, yesText: String = Messages.YES_BUTTON, noText: String = Messages.NO_BUTTON, icon: Icon? = null): Boolean {
  return Messages.showYesNoDialog(project, message, title, yesText, noText, icon) == Messages.YES
}

fun showOkNoDialog(@Nls(capitalization = Nls.Capitalization.Title) title: String, message: String, project: Project?, yesText: String = Messages.OK_BUTTON, noText: String = Messages.NO_BUTTON, icon: Icon? = null): Boolean {
  return Messages.showYesNoDialog(project, message, title, yesText, noText, icon) == Messages.YES
}
