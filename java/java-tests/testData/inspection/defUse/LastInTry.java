class C {
  interface UUID {}
  
  interface Entity {}
  
  interface Repo {
    Entity load(UUID id);
    Entity save(Entity e);
  }
  
  Repo repo;
  
  public void test(UUID id) {
    Entity entity = repo.load(id);
    try {
      <warning descr="The value repo.save(entity) assigned to 'entity' is never used">entity</warning> = repo.save(entity);
    } catch (RuntimeException e) {
      System.out.println("failed to save entity: " + entity);
    }
  }
}