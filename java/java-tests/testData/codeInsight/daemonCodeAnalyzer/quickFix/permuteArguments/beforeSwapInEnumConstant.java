// "Permute arguments" "false"
enum SomeEnum {
    VALUE_ONE(false, true),
    VALUE_TWO(VALUE_ONE, fal<caret>se, false);

    private final boolean flagOne;
    private final boolean flagTwo;
    private final SomeEnum parent;

    SomeEnum(boolean flagOne, boolean flagTwo) {
        this(flagOne, flagTwo, null);
    }

    SomeEnum(boolean flagOne, boolean flagTwo, SomeEnum parent) {
        this.flagOne = flagOne;
        this.flagTwo = flagTwo;
        this.parent = parent;
    }
}