// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.messages

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.DetailedDescription
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.util.Function
import com.intellij.util.PairFunction
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import javax.swing.Icon
import javax.swing.JCheckBox
import javax.swing.JTextField

/**
 * An internal interface to different backend implementations.
 * <strong>Do not call directly - the API is subject to change without prior notice</strong>,
 * use [com.intellij.openapi.ui.Messages] or [com.intellij.openapi.ui.MessageDialogBuilder] instead.
 */
@ApiStatus.Internal
interface MessagesService {
  companion object {
    @JvmStatic
    fun getInstance(): MessagesService =
      ApplicationManager.getApplication()?.getService(MessagesService::class.java)
      ?: MessagesService::class.java.classLoader.loadClass("com.intellij.ui.messages.MessagesServiceImpl").getDeclaredConstructor().newInstance() as MessagesService
  }

  fun showMessageDialog(project: Project?,
                        parentComponent: Component? = null,
                        @DialogMessage message: String?,
                        @NlsContexts.DialogTitle title: String?,
                        options: Array<String>,
                        defaultOptionIndex: Int = 0,
                        focusedOptionIndex: Int = -1,
                        icon: Icon?,
                        doNotAskOption: DoNotAskOption?,
                        alwaysUseIdeaUI: Boolean = false,
                        helpId: String? = null): Int

  fun showMoreInfoMessageDialog(project: Project?,
                                @DialogMessage message: String?,
                                @NlsContexts.DialogTitle title: String?,
                                moreInfo: @DetailedDescription String?,
                                options: Array<String?>?,
                                defaultOptionIndex: Int,
                                focusedOptionIndex: Int,
                                icon: Icon?): Int

  fun showTwoStepConfirmationDialog(@DialogMessage message: String?,
                                    @NlsContexts.DialogTitle title: String?,
                                    options: Array<String?>?,
                                    @NlsContexts.Checkbox checkboxText: String?,
                                    checked: Boolean,
                                    defaultOptionIndex: Int,
                                    focusedOptionIndex: Int,
                                    icon: Icon?,
                                    exitFunc: PairFunction<in Int?, in JCheckBox?, Int?>?): Int

  @NlsSafe
  fun showPasswordDialog(project: Project?,
                         @DialogMessage message: String?,
                         @NlsContexts.DialogTitle title: String?,
                         icon: Icon?,
                         validator: InputValidator?): String?

  fun showPasswordDialog(parentComponent: Component,
                         @DialogMessage message: String?,
                         @NlsContexts.DialogTitle title: String?,
                         icon: Icon?,
                         validator: InputValidator?): CharArray?

  @NlsSafe 
  fun showInputDialog(project: Project?,
                      parentComponent: Component?,
                      @DialogMessage message: String?,
                      @NlsContexts.DialogTitle title: String?,
                      icon: Icon?,
                      initialValue: String?,
                      validator: InputValidator?,
                      selection: TextRange?,
                      comment: @DetailedDescription String?): String?

  @NlsSafe
  fun showMultilineInputDialog(project: Project?,
                               @DialogMessage message: String?,
                               @NlsContexts.DialogTitle title: String?,
                               initialValue: String?,
                               icon: Icon?,
                               validator: InputValidator?): String?

  fun showInputDialogWithCheckBox(@DialogMessage message: String?,
                                  @NlsContexts.DialogTitle title: String?,
                                  @NlsContexts.Checkbox checkboxText: String?,
                                  checked: Boolean,
                                  checkboxEnabled: Boolean,
                                  icon: Icon?,
                                  initialValue: String?,
                                  validator: InputValidator?): Pair<String?, Boolean?>

  @NlsSafe
  fun showEditableChooseDialog(@DialogMessage message: String?,
                               @NlsContexts.DialogTitle title: String?,
                               icon: Icon?,
                               values: Array<String?>?,
                               @NlsSafe initialValue: String?,
                               validator: InputValidator?): String?

  fun showChooseDialog(project: Project?,
                       parentComponent: Component?,
                       @DialogMessage message: String?,
                       @NlsContexts.DialogTitle title: String?,
                       values: Array<String?>?,
                       initialValue: String?,
                       icon: Icon?): Int

  fun showTextAreaDialog(textField: JTextField?,
                         @NlsContexts.DialogTitle title: String?,
                         dimensionServiceKey: String?,
                         parser: Function<in String?, out MutableList<String?>?>?,
                         lineJoiner: Function<in MutableList<String?>?, String?>?)

  fun showErrorDialog(project: Project?,
                      message: @DialogMessage String?,
                      title: @NlsContexts.DialogTitle String)

}
