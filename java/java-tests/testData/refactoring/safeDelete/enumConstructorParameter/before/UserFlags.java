public enum UserFlags {
    Root("Has super administrative powers", 0),
    Blacklisted("Probably a spammer", 1);

    public final String description;
    public final int mask;

    UserFlags(String description, int <caret>position) {
        this.description = description;
        this.mask = 1 << ordinal();
    }
}