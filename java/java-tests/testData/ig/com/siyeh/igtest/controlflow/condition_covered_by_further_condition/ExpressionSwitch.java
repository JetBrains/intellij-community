package com.siyeh.igtest.controlflow.pointless_null_check;

public class ExpressionSwitch {
  // IDEA-259043
  public static final void main(String[] args) throws Exception {
    String s = args.length == 0 ? null : args[0];
    boolean xyz = s != null && switch (s) {
      case "a" -> true;
      default -> false;
    };
  }
}