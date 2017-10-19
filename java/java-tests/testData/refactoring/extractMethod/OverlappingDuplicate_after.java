public class OverlappingDuplicate {
    private void build(String fieldName, StringBuilder builder) {
        builder.append("");
        builder.append(fieldName);
        builder.append("");

        newMethod(builder);
        builder.append("");
    }

    private void newMethod(StringBuilder builder) {
        builder.append("a");
        builder.append("b");
        builder.append("c");
        builder.append("d");
    }
}
