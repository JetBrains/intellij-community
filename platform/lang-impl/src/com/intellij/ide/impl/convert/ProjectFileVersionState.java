package com.intellij.ide.impl.convert;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.ArrayList;
import java.util.List;

/**
* @author nik
*/
public class ProjectFileVersionState {
  private List<String> myPerformedConversionIds = new ArrayList<>();

  @Tag("performed-conversions")
  @AbstractCollection(surroundWithTag = false, elementTag = "converter", elementValueAttribute = "id")
  public List<String> getPerformedConversionIds() {
    return myPerformedConversionIds;
  }

  public void setPerformedConversionIds(List<String> performedConversionIds) {
    myPerformedConversionIds = performedConversionIds;
  }
}
