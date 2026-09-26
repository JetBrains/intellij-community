class Super {
    private static class MethodCandidate {
    }

    static {
        List<Super.MethodCandidate> l = new ArrayList<>();
        List<Super.MethodCandidate1> l1 = new ArrayList<>();
    }
    
    private static class MethodCandidate1 {
    }
}