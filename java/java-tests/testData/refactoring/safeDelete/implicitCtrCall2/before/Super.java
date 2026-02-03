    class Super {
        public <caret>Super() {
        } //Safe Delete should not allow this to be deleted without first changing class Sub's constructors to use Super(String)

        public Super(String name) {
        }
    }
