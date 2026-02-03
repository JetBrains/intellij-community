public class InlineThis {
    public InlineThis() {
        System.out.println("code block here");
    }

    public InlineThis(int i) {
        th<caret>is();
    }

    public InlineThis(String str) {
        this(Integer.parseInt(str));
    }




    public static void main(String[] args) {
        InlineThis aInlineThis = new InlineThis();
        InlineThis aInlineThis1 = new InlineThis(1);
    }
}