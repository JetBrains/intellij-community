// "Convert to record class" "true"
// no "true-preview" above because of IDEA-369873
record R(int myFirst) {
    R(int myFirst) {
        this.myFirst = myFirst;
        System.out.println("hello there, myFirst: " + myFirst + ", first: " + myFirst);
        // Renaming usages in strings can be enabled with 'RenameRefactoring.setSearchInComments(false)', but it always prompts, so it's a no-go
    }

    // first
    // myFirst
}
