// "Fix all 'Redundant 'String' operation' problems in file" "true"
class Foo {
  public static void main(String[] args) {
    StringBuilder sb = new StringBuilder();

    int i = Integer.parseInt(args[3]);
    int j = Integer.parseInt(args[4]);

    sb.append(args[0].charAt(0));
    sb.append(args[0].charAt(2));
    sb.append(args[0].charAt(i));

    sb.append(args[0].charAt(i - 3));
    sb.append(args[0].substring(i - 3, 2 - i));
    sb.append(args[0].substring(3 - i, i - 2));
    sb.append(args[0].substring(3 - i, 2 - i));
    sb.append(args[0].charAt(2 - i));

    sb.append(args[0].substring(2 - i, 4 - i));
    sb.append(args[0].substring(i - 2, i - 4));

    sb.append(args[0].charAt(i + i));

    sb.append(args[0].charAt(i + 2));
    sb.append(args[0].charAt(2 + i));
    sb.append(args[0].charAt(i + 2));
    sb.append(args[0].charAt(2 + i));

    sb.append(args[0].substring(2 + i, 4 + i));
    sb.append(args[0].substring(i + 2, i + 4));

    sb.append(new /*1

      */String(/* 2 */"foo")/*3    */./*    4*/charAt(i/*5\n*/+2 /*\n\r6 */))

    String s1 = "xxx" + args[0].substring(3, 5);
    String s2 = "xxx" + args[0].charAt(3);
    String s3 = args[0].charAt(2) + "xxx";

    System.out.print(args[0].charAt(2));
    System.out.println(args[0].charAt(2));

    System.out.print(args[0].substring(2, 4));
    System.out.println(args[0].substring(2, 4));

    sb.append(args[0].charAt(i + j + 1));
    sb.append(args[0].charAt(i + j + (j + 1)));

    sb.append(args[0].substring(i + j + 2 * j, i + j + 3 * j));
    sb.append(args[0].substring(i + j + 3 * j, i + j + 2 * j));

    sb.append(args[0].charAt(i - j - 2));
    sb.append(args[0].charAt(i - (j + 2)));

    sb.append(args[0].charAt(2 - (j + 2)));
    sb.append(args[0].charAt(2 - ((2 - i) - j)));
    sb.append(args[0].charAt(i - (-2*j + 3)));
    sb.append(args[0].substring(i + j * 2, i + j * 3));
    sb.append(args[0].substring(i + j * 3, i + j * 2));

    sb.append(args[0].charAt(i - ((j - 1) - (j + 1))));
  }
}