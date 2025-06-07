public record FormatRecord(
        @EmptyFieldAnno1
        @EmptyFieldAnno2
        @FieldAnnoWithParameters(value = "val", arr = {"foo", "bar", "baz"})
        String s,


        @EmptyFieldAnno2
        @FieldAnnoWithParameters(value = "val", arr = {"foo", "bar", "baz"})
        String t
) {
}