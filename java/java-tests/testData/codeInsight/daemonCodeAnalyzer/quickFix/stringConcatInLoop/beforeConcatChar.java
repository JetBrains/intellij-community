// "Convert variable 'str1' from String to StringBuilder" "true"

public class IntentionBug {
    public static void main(String[] args) {
        String str1 = "";
        for (char i = 'a'; i < 'd'; i++) {
            str1 = str1 <caret>+ i + (char) (i + 1);
            str1 = /*1*/str1/*2*/+ i + (char) (i + 1);
            str1 = str1+1+2+3;
            str1 = str1+(1+2+3);
        }
        System.out.println(str1);
   }
}