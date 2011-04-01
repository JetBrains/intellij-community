public enum UserFlags {
    Root("Has super administrative powers"),
    Blacklisted("Probably a spammer");

    public final String description;
    public final int mask;

    UserFlags(String description) {
        this.description = description;
        this.mask = 1 << ordinal();
    }
}