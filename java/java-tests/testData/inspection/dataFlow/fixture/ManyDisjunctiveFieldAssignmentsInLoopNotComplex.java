import org.jetbrains.annotations.NotNull;

class Some {
  String field1;
  String field2;
  String field3;
  String field4;
  String field5;
  String field6;
  String field7;
  String field8;
  String field9;

  void a(String[] lines) {
    for (String line : lines) {
      if (line.startsWith("1")) field1 = someString();
      else if (line.startsWith("2")) field2 = someString();
      else if (line.startsWith("3")) field3 = someString();
      else if (line.startsWith("4")) field4 = someString();
      else if (line.startsWith("5")) field5 = someString();
      else if (line.startsWith("6")) field6 = someString();
      else if (line.startsWith("7")) field7 = someString();
      else if (line.startsWith("8")) field8 = someString();
      else if (line.startsWith("9")) field9 = someString();
    }
    System.out.println(field1);
    System.out.println(field2);
    System.out.println(field3);
    System.out.println(field4);
    System.out.println(field5);
    System.out.println(field6);
    System.out.println(field7);
    System.out.println(field8);
    System.out.println(field9);

  }
  
  @NotNull String someString() { return ""; }

}


