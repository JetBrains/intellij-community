public class InlineThis {
    public InlineThis() {
        System.out.println("code block here");
    }

    public InlineThis(int i) {
        this();
    }

    public InlineThis(String str) {
        th<caret>is(Integer.parseInt(str));
    }




    public static void main(String[] args) {
        InlineThis aInlineThis = new InlineThis();
        InlineThis aInlineThis1 = new InlineThis(1);
    }
}