// "Fix all 'Redundant 'String' operation' problems in file" "true"
class Foo {
  public static void main(String[] args) {
    StringBuilder sb = new StringBuilder();

    int i = Integer.parseInt(args[3]);
    int j = Integer.parseInt(args[4]);

    sb.append(args[0].sub<caret>string(0, 1));
    sb.append(args[0].substring(2, 3));
    sb.append(args[0].substring(i, i + 1));

    sb.append(args[0].substring(i - 3, i - 2));
    sb.append(args[0].substring(i - 3, 2 - i));
    sb.append(args[0].substring(3 - i, i - 2));
    sb.append(args[0].substring(3 - i, 2 - i));
    sb.append(args[0].substring(2 - i, 3 - i));

    sb.append(args[0].substring(2 - i, 4 - i));
    sb.append(args[0].substring(i - 2, i - 4));

    sb.append(args[0].substring(i + i, i + (i + 1)));

    sb.append(args[0].substring(i + 2, i + 3));
    sb.append(args[0].substring(2 + i, i + 3));
    sb.append(args[0].substring(i + 2, 3 + i));
    sb.append(args[0].substring(2 + i, 3 + i));

    sb.append(args[0].substring(2 + i, 4 + i));
    sb.append(args[0].substring(i + 2, i + 4));

    sb.append(new /*1

      */String(/* 2 */"foo")/*3    */./*    4*/substring(i/*5\n*/+2, i/*\n\r6 */+3))

    String s1 = "xxx" + args[0].substring(3, 5);
    String s2 = "xxx" + args[0].substring(3, 4);
    String s3 = args[0].substring(2, 3) + "xxx";

    System.out.print(args[0].substring(2, 3));
    System.out.println(args[0].substring(2, 3));

    System.out.print(args[0].substring(2, 4));
    System.out.println(args[0].substring(2, 4));

    sb.append(args[0].substring(i + j + 1, i + j + 2));
    sb.append(args[0].substring(i + j + (j + 1), i + j + (j + 2)));

    sb.append(args[0].substring(i + j + 2 * j, i + j + 3 * j));
    sb.append(args[0].substring(i + j + 3 * j, i + j + 2 * j));

    sb.append(args[0].substring(i - j - 2, i - j - 1));
    sb.append(args[0].substring(i - (j + 2), i - (j + 1)));

    sb.append(args[0].substring(2 - (j + 2), 3 - (j + 2)));
    sb.append(args[0].substring(2 - ((2 - i) - j), 3 - ((2 - i) - j)));
    sb.append(args[0].substring(i - (-2*j + 3), i - (-2*j + 2) ));
    sb.append(args[0].substring(i + j * 2, i + j * 3));
    sb.append(args[0].substring(i + j * 3, i + j * 2));

    sb.append(args[0].substring(i - ((j - 1) - (j + 1)), i - ((j - 2) - (j + 1))));
  }
}