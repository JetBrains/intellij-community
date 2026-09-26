class Action {
	public static void main(String[] args) {
		var a = new Person("sdf", "sdf");
		var b = new PersonB("sdf", "sdf");
	}
	
	public static class PersonB {

		private final String name;
		private final String address;

		public PersonB(String name, String address) {
			this.name = name;
			this.address = address;
		}
	}
}
record Person(String name, String address) {
	public Person(String name) {
		this(name, "");
	}
}