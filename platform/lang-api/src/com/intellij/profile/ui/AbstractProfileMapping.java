/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.profile.ui;

import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.util.Condition;

import javax.swing.*;

/**
 * User: anna
 * Date: 01-Dec-2005
 */
public interface AbstractProfileMapping extends UnnamedConfigurable {
  String getProfileColumnTitle();
  String getScopeColumnTitle();
  Condition<String> openEditProfilesDialog();

  interface MapRule {
    String getProfile();
    String getScopeName();
    boolean isProperProfile();
    String assignProfile(String profile);
    void setProfile(String profile);
    void deassignProfile();
    Icon getIcon(final boolean expanded);
    boolean canBeEdited();
    void reset();
    boolean isRoot();
  }
}
