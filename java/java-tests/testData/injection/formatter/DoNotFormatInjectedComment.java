public class X {

    public static void main(String[] args) {
        //language=JAVA
        fixture.addClass("<caret>package demo;" +
                         "public enum MyEnum {" +
                         "" +
                         " /**" +
                         "  * Dead <b>easy</b>. <em>Another</em> sentence." +
                         "  * <p/>" +
                         "  * yyy tever." +
                         "  * @see #ordinal()" +
                         "  * @since 15" +
                         "  */" +
                         " SIMPLE," +
                         "" +
                         " /**" +
                         "  * Ouch." +
                         "  */" +
                         " HARD," +
                         "" +
                         " /**" +
                         "  * Madness." +
                         "  */" +
                         " EVIL" +
                         "}");
    }
}