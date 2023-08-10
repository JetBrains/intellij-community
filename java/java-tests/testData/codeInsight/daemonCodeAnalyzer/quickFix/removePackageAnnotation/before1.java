// "Remove" "true-preview"
@Anno(value=42)<caret>
package com.example;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.PACKAGE)
@interface Anno {
  int value();
}
