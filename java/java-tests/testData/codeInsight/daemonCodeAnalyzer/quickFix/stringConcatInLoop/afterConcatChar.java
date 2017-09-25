// "Convert variable 'str1' from String to StringBuilder" "true"

public class IntentionBug {
    public static void main(String[] args) {
        /*1*/
        /*2*/
        StringBuilder str1 = new StringBuilder();
        for (char i = 'a'; i < 'd'; i++) {
            str1.append(i).append((char) (i + 1));
            str1.append(i).append((char) (i + 1));
            str1.append(1).append(2).append(3);
            str1.append(1 + 2 + 3);
        }
        System.out.println(str1);
   }
}