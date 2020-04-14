// "Fix all 'Redundant String operation' problems in file" "true"
class Foo {
  public static void main(String[] args) {
    StringBuilder sb = new StringBuilder();

    int i = Integer.parseInt(args[4]);

    sb.append(args[0].charAt(0));
    sb.append(args[0].charAt(2));
    sb.append(args[0].charAt(i));

    sb.append(args[0].charAt(i + 2));
    sb.append(args[0].charAt(2 + i));
    sb.append(args[0].charAt(i + 2));
    sb.append(args[0].charAt(2 + i));

    sb.append(args[0].substring(2 + i, 4 + i));
    sb.append(args[0].substring(i + 2, i + 4));

    String s1 = "xxx" + args[0].substring(3, 5);
    String s2 = "xxx" + args[0].charAt(3);
    String s3 = args[0].charAt(2) + "xxx";

    boolean value = args[0].charAt(4) == '_';

    if(args[0].charAt(4) == '_') { }
    if(args[0].charAt(4) != '_') { }
    if(args[0].charAt(4) == '_') { }
    if(args[0].charAt(4) != '_') { }

    System.out.print(args[0].charAt(2));
    System.out.println(args[0].charAt(2));

    System.out.print(args[0].substring(2, 4));
    System.out.println(args[0].substring(2, 4));
  }
}