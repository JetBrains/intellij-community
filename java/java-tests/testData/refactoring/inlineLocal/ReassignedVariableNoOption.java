class X {{
  String bla = "bla";

  System.out.println(bla);
  bla<caret> = "bla3";
  System.out.println(bla);
  System.out.println(bla);
  bla = "bla4";
  System.out.println(bla);
}}