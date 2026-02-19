// "Convert to record class" "true-preview"
record R(int myFirst) {
    R(int myFirst) {
        this.myFirst = myFirst;
        System.out.println("hello there, myFirst: " + myFirst + ", first: " + myFirst);
        // Renaming usages in strings can be enabled with 'RenameRefactoring.setSearchInComments(false)', but it always prompts, so it's a no-go
    }

    // first
    // myFirst
}
