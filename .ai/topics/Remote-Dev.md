# Remote Development

## Basics

- Structure modules using proper suffixes:
  - `.frontend` - UI code that runs on client side
  - `.backend` - computation code that runs on server side
  - `.shared` - code common to both frontend and backend
- Use RPC for frontend-backend communication:
  - Create interfaces with `@Rpc` annotation in shared module
  - Implement interfaces on backend side
  - Use `RemoteApiProvider` to register implementations
  - All RPC methods must be `suspend` functions
  - All parameters and return values must be `@Serializable`
- When working with backend objects on frontend:
  - Use ID serialization (e.g., `VirtualFile.rpcId()` and `VirtualFileId.virtualFile()`)
  - For state synchronization, use `StateFlow` and `Flow`
- Don't access on frontend:
  - PSI, indexes, file system (directly), VCS
  - Debugger, build systems, modules
  - Any backend-only services and APIs

## Documentation

Detailed documentation in [docs/IntelliJ-Platform/4_man/Remote-Development/](../docs/IntelliJ-Platform/4_man/Remote-Development/):

- [Directory Overview](../docs/IntelliJ-Platform/4_man/Remote-Development/directory.md) - Remote development architecture overview
- [Module Split](../docs/IntelliJ-Platform/4_man/Remote-Development/0_Module-Split.md) - How to split modules for remote dev
- [Plugin Directory Structure](../docs/IntelliJ-Platform/4_man/Remote-Development/1_Plugin-Directory-Structure.md) - Directory organization
- [How to Split a Plugin](../docs/IntelliJ-Platform/4_man/Remote-Development/2_How-to-Split-a-Plugin.md) - Step-by-step guide
- [RPC Guidelines](../docs/IntelliJ-Platform/4_man/Remote-Development/RPC-Guideline.md) - RPC implementation patterns
- [How to Split Module to Frontend/Backend](../docs/IntelliJ-Platform/4_man/Remote-Development/How-to-split-module-to-frontend-and-backend.md) - Module separation
- [Remote Dev Actions](../docs/IntelliJ-Platform/4_man/Remote-Development/Remote-Dev-Actions.md) - Action implementation
- [Settings Synchronization](../docs/IntelliJ-Platform/4_man/Remote-Development/Settings-synchronisation-in-Remote-Development-and-CodeWithMe.md) - Settings sync
- [Rhizome Guidelines](../docs/IntelliJ-Platform/4_man/Remote-Development/Rhizome-guidelines-(-shared-database-).md) - Shared database pattern

**External:** [Remote Development Overview](https://www.jetbrains.com/help/idea/remote-development-overview.html) (user documentation)


### Creating Remote Development Modules
#### Module Types and Naming Conventions

- **Standard Module**: `intellij.<pluginGroup>.<frameworkName>`
  Example: `intellij.java.dsm`, `intellij.cidr.debugger`

- **Remote Development Modules** (split architecture):
  - **Shared Module**: `intellij.<feature>`
  - **RPC Module**: `intellij.<feature>.rpc` (for RPC interfaces)
  - **Backend Module**: `intellij.<feature>.backend`
  - **Frontend Module**: `intellij.<feature>.frontend`
  - **Split Modules**: `intellij.<feature>.frontend.split`, `intellij.<feature>.backend.split`

#### For Platform Modules

1. **Shared Module**:

- If working with existing module: Keep as is
- For new module: Create in `platform/feature/` following V2 plugin format

2. **RPC Module** (if needed):

- Create in `platform/feature-rpc/` for RPC interfaces
- Extract all `@Rpc` interfaces, DTOs, and supporting classes
- Add dependencies on required serialization libraries
- This module should have no implementation logic

3. **Backend Module**:

- Create in `platform/feature/backend/`
- Include `intellij.platform.feature.backend.xml` in resources
- Add dependency on `intellij.platform.backend`
- Add to `essential-modules.xml`

4. **Frontend Module**:

- Create in `platform/feature/frontend/`
- Include `intellij.platform.feature.frontend.xml` in resources
- Add dependency on `intellij.platform.frontend`
- Add to `essential-modules.xml` and `intellij.platform.frontend.main.iml`
- Add to JetBrains Client's product-modules.xml

5. **Frontend Split Module** (if needed):

- Create in `remote-dev/feature/frontend.split`
- Configure as V2 plugin module
- Add to product-modules.xml and JetBrainsClientPlugin.xml

#### For Plugins in Monorepo

1. **Shared Module**:

- Keep shared code in main plugin module
- Register in appropriate product-modules.xml for bundling

2. **RPC Module**:

- Create with `.rpc` suffix for RPC interfaces
- Contains only RPC interfaces, DTOs, and supporting classes
- Register in plugin.xml
- Both frontend and backend modules depend on this

3. **Backend Module**:

- Create with `.backend` suffix as V2 plugin module
- Add dependency on `intellij.platform.backend` and `.rpc` module
- Register in plugin.xml

4. **Frontend Module**:

- Create with `.frontend` suffix as V2 plugin module
- Add dependency on `intellij.platform.frontend` and `.rpc` module
- Register in plugin.xml

5. **Frontend Split Module**:

- Create with `.frontend.split` suffix in appropriate location
- Add dependency on `intellij.platform.frontend.split`
- Register in plugin.xml

## Reactive Programming Patterns for Remote Development

### Key Patterns for Reactive State in Remote Development

The following patterns are essential when implementing reactive UI components for Remote Development:

1. **Backend StateFlow with Initial Value Transfer:**

- Use StateFlow for all mutable state on the backend
- Add initial values in DTOs to avoid blocking calls on the frontend
- Use `.toRpc()` extension to convert StateFlow to RpcFlow for RPC transfer

2. **Converting RpcFlow to StateFlow on Frontend:**

- Use `toFlow().stateIn()` pattern to convert RpcFlow to StateFlow
- Always provide initial values from DTO to ensure immediate UI feedback
- Use SharingStarted.Eagerly for UI components that need immediate updates

3. **Example: Reactive Breakpoint DTO**
   ```kotlin
   // Flow-based DTO with initial values
   @Serializable
   data class XBreakpointDto(
     // Static properties
     val displayText: String,
     val iconId: IconId,
     val sourcePosition: XSourcePositionDto?,
     
     // Initial values for immediate use
     val initialEnabled: Boolean,
     val initialSuspendPolicy: SuspendPolicy,
     
     // Reactive flow fields
     val enabledState: RpcFlow<Boolean>,
     val suspendPolicyState: RpcFlow<SuspendPolicy>,
   )
   ```

4. **Example: Frontend Component with Reactive State**
   ```kotlin
   class FrontendXBreakpointProxy(
     private val project: Project,
     private val cs: CoroutineScope,
     private val dto: XBreakpointDto
   ) {
     // Convert RpcFlow to StateFlow with initial values
     val enabled: StateFlow<Boolean> = dto.enabledState.toFlow()
       .stateIn(cs, SharingStarted.Eagerly, dto.initialEnabled)
       
     // Access current value from StateFlow
     fun isEnabled(): Boolean = enabled.value
   }
   ```
