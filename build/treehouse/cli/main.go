// Command cli is the Treehouse CLI of the upstream project, built from this repository.
//
// The wrapper in the parent directory starts this binary. It is a separate binary, so the
// wrapper keeps a pure standard-library dependency set.
package main

import (
	"os"

	treehousecmd "github.com/kunchenguid/treehouse/cmd"
)

// main starts the upstream command tree and never calls treehousecmd.SetVersion.
//
// The omission is the mechanism, not an oversight. The `version` package variable of
// `cmd` then keeps its "dev" default, and its PersistentPreRun returns at once. That one
// fact stops every update-cache read, every api.github.com request and every detached
// background child. The TREEHOUSE_NO_UPDATE_CHECK=1 variable that the launcher and the
// wrapper set is the second, independent switch for the same check.
//
// The root main of the upstream project also has an `--update-check` branch that calls
// `internal/updater`. This binary cannot reproduce it, because Go blocks an `internal`
// import from another module. It does not need it: nothing passes that flag once the
// update check is off.
//
// os.Exit(1), never a panic. A panic would turn a guard failure from exit 1 into exit 2
// and would corrupt the exit-code contract of the wrapper.
func main() {
	if err := treehousecmd.Execute(); err != nil {
		os.Exit(1)
	}
}
