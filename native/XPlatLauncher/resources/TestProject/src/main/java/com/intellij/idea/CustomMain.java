package com.intellij.idea;

public class CustomMain {
  public static void main(String[] args) {
    if (args.length > 0 && args[0].equals("custom-command")) {
      System.out.println("Custom command: product.property=" + System.getProperty("product.property") + ", custom.property=" + System.getProperty("custom.property"));
    }
    else {
      System.err.println("This class may be used to run custom-command only");
      System.exit(1);
    }
  }
}
