// "Inline variable|->Inline and rename 'vExposure' to 'exp'" "true-preview"
package com.example;

import java.util.ArrayList;
import java.util.List;

public class Demo {
  static class ExposureSpecification {
  }

  public static void main(String[] args) {
    List<ExposureSpecification> vExposures = new ArrayList<>();

    for (ExposureSpecification exp : vExposures) {
        // ... lines of code using exp ...
      System.out.println(exp);
    }
  }
}