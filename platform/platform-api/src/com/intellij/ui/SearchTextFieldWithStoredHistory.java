/*
 * @author max
 */
package com.intellij.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;

public class SearchTextFieldWithStoredHistory extends SearchTextField {
  private final String myPropertyName;

  public SearchTextFieldWithStoredHistory(@NonNls final String propertyName) {
    myPropertyName = propertyName;
    reset();
  }

  public void addCurrentTextToHistory() {
    super.addCurrentTextToHistory();
    PropertiesComponent.getInstance().setValue(myPropertyName, StringUtil.join(getHistory(), "\n"));
  }

  public void reset() {
    final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    final String history = propertiesComponent.getValue(myPropertyName);
    if (history != null) {
      final String[] items = history.split("\n");
      ArrayList<String> result = new ArrayList<String>();
      for (String item : items) {
        if (item != null && item.length() > 0) {
          result.add(item);
        }
      }
      setHistory(result);
      setSelectedItem("");
    }
  }
}