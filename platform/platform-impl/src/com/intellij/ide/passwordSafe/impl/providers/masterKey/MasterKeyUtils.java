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
package com.intellij.ide.passwordSafe.impl.providers.masterKey;

import com.intellij.ui.DocumentAdapter;
import com.intellij.util.Processor;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.util.Arrays;

/**
 * Utilities for master key provider
 */
class MasterKeyUtils {
  /**
   * The private constructor for utility class
   */
  private MasterKeyUtils() {
  }

  /**
   * Match passwords
   *
   * @param passwordField1 the first password field
   * @param passwordField2 the second password field
   * @param setError       the callback used to set or to clear an error
   */
  static void matchPasswords(final JPasswordField passwordField1, final JPasswordField passwordField2, final Processor<String> setError) {
    DocumentAdapter l = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        if (Arrays.equals(passwordField1.getPassword(), passwordField2.getPassword())) {
          setError.process(null);
        }
        else {
          setError.process("The new password and confirm passwords do not match.");
        }
      }
    };
    passwordField1.getDocument().addDocumentListener(l);
    passwordField2.getDocument().addDocumentListener(l);
  }
}
