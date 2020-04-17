// "Fix all 'Redundant String operation' problems in file" "true"
class Foo {
  public static void main(String[] args) {
    StringBuilder sb = new StringBuilder();

    int i = Integer.parseInt(args[4]);

    sb.append(args[0].sub<caret>string(0, 1));
    sb.append(args[0].substring(2, 3));
    sb.append(args[0].substring(i, i + 1));

    sb.append(args[0].substring(i + 2, i + 3));
    sb.append(args[0].substring(2 + i, i + 3));
    sb.append(args[0].substring(i + 2, 3 + i));
    sb.append(args[0].substring(2 + i, 3 + i));

    sb.append(args[0].substring(2 + i, 4 + i));
    sb.append(args[0].substring(i + 2, i + 4));

    String s1 = "xxx" + args[0].substring(3, 5);
    String s2 = "xxx" + args[0].substring(3, 4);
    String s3 = args[0].substring(2, 3) + "xxx";

    boolean value = args[0].substring(4, 5).equals("_");

    if(args[0].substring(4, 5).equals("_")) { }
    if(!args[0].substring(4, 5).equals("_")) { }
    if(!!args[0].substring(4, 5).equals("_")) { }
    if(!!!!!args[0].substring(4, 5).equals("_")) { }

    System.out.print(args[0].substring(2, 3));
    System.out.println(args[0].substring(2, 3));

    System.out.print(args[0].substring(2, 4));
    System.out.println(args[0].substring(2, 4));
  }
}