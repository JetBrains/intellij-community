interface Am {
    String getIc<caret>on();
}

class AIIm implements Am {
    @Override
    public String getIcon() {
        return null;
    }

    public static void main(String[] args) {
        new AIIm().getIcon();
    }
}