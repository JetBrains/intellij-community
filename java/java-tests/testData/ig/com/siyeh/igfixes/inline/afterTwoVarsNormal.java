// "Inline variable|->Keep the 'vExposure' variable" "true-preview"
package com.example;

import java.util.ArrayList;
import java.util.List;

public class Demo {
  static class ExposureSpecification {
  }

  public static void main(String[] args) {
    List<ExposureSpecification> vExposures = new ArrayList<>();

    for (ExposureSpecification vExposure : vExposures) {
        // ... lines of code using exp ...
      System.out.println(vExposure);
    }
  }
}