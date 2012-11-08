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
package com.intellij.application.options.codeStyle.arrangement.match;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 11/7/12 3:00 PM
 */
@SuppressWarnings("unchecked")
public class ArrangementMatchingRulesListModel extends DefaultListModel {

  private static final Logger LOG = Logger.getInstance("#" + ArrangementMatchingRulesListModel.class.getName());

  @Override
  public Object set(int index, Object element) {
    if (ArrangementConstants.LOG_RULE_MODIFICATION) {
      LOG.info(String.format("Setting match rule '%s' to index %d", element, index));
    }
    return super.set(index, element);
  }

  @Override
  public void setElementAt(Object element, int index) {
    if (ArrangementConstants.LOG_RULE_MODIFICATION) {
      LOG.info(String.format("Setting match rule '%s' to index %d", element, index));
    }
    super.setElementAt(element, index);
  }

  @Override
  public void add(int index, Object element) {
    if (ArrangementConstants.LOG_RULE_MODIFICATION) {
      LOG.info(String.format("Adding match rule '%s' (to index %d)", element, size()));
    }
    super.add(index, element);
  }

  @Override
  public void addElement(Object element) {
    if (ArrangementConstants.LOG_RULE_MODIFICATION) {
      LOG.info(String.format("Adding match rule '%s' (to index %d)", element, size()));
    }
    super.addElement(element);
  }

  @Override
  public Object remove(int index) {
    if (ArrangementConstants.LOG_RULE_MODIFICATION) {
      LOG.info(String.format("Removing match rule '%s' from index %d", getElementAt(index), index));
    }
    return super.remove(index);
  }

  @Override
  public void removeElementAt(int index) {
    if (ArrangementConstants.LOG_RULE_MODIFICATION) {
      LOG.info(String.format("Removing match rule '%s' from index %d", getElementAt(index), index));
    }
    super.removeElementAt(index);
  }
  
  @Override
  public void clear() {
    if (ArrangementConstants.LOG_RULE_MODIFICATION) {
      LOG.info(String.format("Removing all match rules (%d)", size()));
    }
    super.clear();
  }

  @Override
  public void removeAllElements() {
    if (ArrangementConstants.LOG_RULE_MODIFICATION) {
      LOG.info(String.format("Removing all match rules (%d)", size()));
    }
    super.removeAllElements();
  }
}
