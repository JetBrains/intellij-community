public class OverlappingDuplicate {
    private void build(String fieldName, StringBuilder builder) {
        builder.append("");
        builder.append(fieldName);
        builder.append("");
        <selection>
        builder.append("a");
        builder.append("b");
        builder.append("c");
        builder.append("d");</selection>
        builder.append("");
    }
}
