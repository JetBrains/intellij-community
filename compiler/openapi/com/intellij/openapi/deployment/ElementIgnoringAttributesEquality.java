package com.intellij.openapi.deployment;

import gnu.trove.TObjectHashingStrategy;

public class ElementIgnoringAttributesEquality implements TObjectHashingStrategy<ContainerElement> {
  public boolean equals(ContainerElement object, ContainerElement object1) {
    if (object1 == null || object == null) {
      return object == object1;
    }
    return object.equalsIgnoreAttributes(object1);
  }

  public int computeHashCode(ContainerElement object) {
    String presentableName = object.getPresentableName();
    if (presentableName == null) {
      return 0;
    }
    return presentableName.hashCode();
  }
}