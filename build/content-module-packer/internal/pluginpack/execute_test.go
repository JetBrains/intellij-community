package pluginpack

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"reflect"
	"slices"
	"strings"
	"testing"

	"jetbrains.com/content-module-packer/internal/filemetadata"
	"jetbrains.com/content-module-packer/internal/jarpack"
	"jetbrains.com/content-module-packer/internal/javaglob"
)

func TestDistributionScopeUsesOneRemainderWithoutPluginCollision(test *testing.T) {
	root := test.TempDir()
	pluginInput := filepath.Join(root, "plugin-input")
	distributionInput := filepath.Join(root, "distribution-input")
	writeTestFile(test, pluginInput, []byte("plugin"))
	writeTestFile(test, distributionInput, []byte("distribution"))
	excluded := false
	assets := []Asset{
		{Destination: "lib/native.bin", Producer: "remainder"},
		{Destination: "lib/native.bin", Producer: "remainder", ClassPath: &excluded, Scope: DistributionScope},
	}
	recipe := Recipe{
		Version: ScopedVersion, Plugin: "scoped", LayoutSignature: "scoped-v3", Assets: assets,
		Operations: []Operation{
			{Kind: "copy", Destination: "lib/native.bin", Input: &Reference{Artifact: "plugin-input"}},
			{Kind: "copy", Destination: "lib/native.bin", Scope: DistributionScope, Input: &Reference{Artifact: "distribution-input"}},
		},
	}
	catalogue := Catalogue{Version: Version, Artifacts: []Artifact{
		{ID: "plugin-input", Kind: "file", Root: pluginInput},
		{ID: "distribution-input", Kind: "file", Root: distributionInput},
	}}
	execution, err := Plan(recipe, catalogue)
	if err != nil {
		test.Fatal(err)
	}
	output := filepath.Join(root, "remainder")
	inventory := filepath.Join(root, "inventory.json")
	if err := execution.Write(output, inventory); err != nil {
		test.Fatal(err)
	}
	if actual := string(readTestFile(test, filepath.Join(output, "lib/native.bin"))); actual != "plugin" {
		test.Fatalf("plugin payload = %q", actual)
	}
	distributionDestination := TransportDestination(ScopedVersion, DistributionScope, "lib/native.bin")
	if actual := string(readTestFile(test, filepath.Join(output, filepath.FromSlash(distributionDestination)))); actual != "distribution" {
		test.Fatalf("distribution payload = %q", actual)
	}
	entries, err := filemetadata.Read(inventory)
	if err != nil {
		test.Fatal(err)
	}
	if len(entries) != 2 || !slices.ContainsFunc(entries, func(entry filemetadata.Entry) bool {
		return entry.RelativePath == distributionDestination
	}) {
		test.Fatalf("scoped inventory = %+v", entries)
	}
}

// projectionContracts writes the plan file, the input catalogue and the classpath descriptor of one plugin under
// contracts. The plan copies source as the tree resources, or as the file lib/raw.jar. The result is the argument
// list of the packer's projection mode without its four outputs.
func projectionContracts(test *testing.T, contracts string, tree bool, source string) []string {
	test.Helper()
	version, kind := Version, "file"
	asset := map[string]any{"destination": "lib/raw.jar", "inputs": []string{"raw"}}
	if tree {
		version, kind = TreeVersion, "directory"
		asset = map[string]any{"destination": "resources", "inputs": []string{"raw"}, "kind": "tree", "classPath": false}
	}
	plan := map[string]any{"version": version, "plugin": "test.plugin", "variant": "default",
		"layoutSignature": fmt.Sprintf("raw-v%d", version), "assets": []any{asset}}
	catalogue := Catalogue{Version: Version, Artifacts: []Artifact{{ID: "raw", Kind: kind, Root: source}}}
	for name, value := range map[string]any{"plan.json": plan, "catalogue.json": catalogue} {
		data, err := json.Marshal(value)
		if err != nil {
			test.Fatal(err)
		}
		writeTestFile(test, filepath.Join(contracts, name), data)
	}
	writeTestFile(test, filepath.Join(contracts, "descriptor.xml"), []byte("<idea-plugin><id>test.plugin</id></idea-plugin>"))
	return []string{"--projection=" + filepath.Join(contracts, "plan.json"), "--input-catalogue=" + filepath.Join(contracts, "catalogue.json"),
		"--classpath-descriptor=" + filepath.Join(contracts, "descriptor.xml"), "--plugin-directory=plugins/test", fmt.Sprintf("--execution-version=%d", version)}
}

func TestCopyModesPreserveOrOverrideTheSource(test *testing.T) {
	for _, item := range []struct {
		inputMode  os.FileMode
		outputMode uint32
		want       os.FileMode
	}{
		{inputMode: 0o555, outputMode: 0, want: 0o555},
		{inputMode: 0o555, outputMode: 0o644, want: 0o644},
		{inputMode: 0o644, outputMode: 0o755, want: 0o755},
	} {
		test.Run(fmt.Sprintf("input=%o/output=%o", item.inputMode, item.outputMode), func(test *testing.T) {
			root := test.TempDir()
			input := filepath.Join(root, "resource.txt")
			writeTestFile(test, input, []byte("resource"))
			if err := os.Chmod(input, item.inputMode); err != nil {
				test.Fatal(err)
			}
			assets := []Asset{{Destination: "resource.txt", Producer: "remainder"}}
			recipe := Recipe{
				Version: Version, Plugin: "resource", LayoutSignature: "resource-v1", Assets: assets,
				Operations: []Operation{{Kind: "copy", Destination: "resource.txt", Input: &Reference{Artifact: "resource"}, Mode: item.outputMode}},
			}
			catalogue := Catalogue{Version: Version, Artifacts: []Artifact{{ID: "resource", Kind: "file", Root: input}}}
			output, _ := writeExecution(test, recipe, catalogue)
			info, err := os.Stat(filepath.Join(output, "resource.txt"))
			if err != nil {
				test.Fatal(err)
			}
			if info.Mode().Perm() != item.want {
				test.Fatalf("file mode = %o, want %o", info.Mode().Perm(), item.want)
			}
		})
	}
}

func TestTreeModeNormalizationPreservesDeclaredBitsAndClearsGroupWrite(test *testing.T) {
	source := filepath.Join(test.TempDir(), "source")
	writeTestFile(test, filepath.Join(source, "bin/tool"), []byte("tool"))
	writeTestFile(test, filepath.Join(source, "data.txt"), []byte("data"))
	for name, mode := range map[string]os.FileMode{"": 0o770, "bin": 0o731, "bin/tool": 0o771, "data.txt": 0o664} {
		if err := os.Chmod(filepath.Join(source, name), mode); err != nil {
			test.Fatal(err)
		}
	}
	recipe, catalogue := treePlan(source)
	recipe.Assets[0].NormalizeTreeModes = true
	recipe.Operations[0].Mode = 0o644
	output, _ := writeExecution(test, recipe, catalogue)
	for name, want := range map[string]os.FileMode{
		"kotlinc": 0o750, "kotlinc/bin": 0o711, "kotlinc/bin/tool": 0o751, "kotlinc/data.txt": 0o644,
	} {
		info, err := os.Stat(filepath.Join(output, name))
		if err != nil || info.Mode().Perm() != want {
			test.Fatalf("%s mode = %v, want %o: %v", name, info, want, err)
		}
	}
}

func TestCopyTreePreservesItsOwnOutputsAndEmptyRoot(test *testing.T) {
	for _, empty := range []bool{false, true} {
		test.Run(fmt.Sprint(empty), func(test *testing.T) {
			root := test.TempDir()
			source := filepath.Join(root, "source")
			if err := os.MkdirAll(source, 0o750); err != nil {
				test.Fatal(err)
			}
			if !empty {
				writeTestFile(test, filepath.Join(source, "bin/tool"), []byte("executable"))
				if err := os.Chmod(filepath.Join(source, "bin/tool"), 0o751); err != nil {
					test.Fatal(err)
				}
				writeTestFile(test, filepath.Join(source, "lib/compiler.jar"), []byte("not classpath"))
				if err := os.MkdirAll(filepath.Join(source, "empty/nested"), 0o710); err != nil {
					test.Fatal(err)
				}
				if err := os.Symlink("./bin/tool", filepath.Join(source, "current")); err != nil {
					test.Fatal(err)
				}
				if err := os.Symlink("empty", filepath.Join(source, "empty-link")); err != nil {
					test.Fatal(err)
				}
			}
			recipe, catalogue := treePlan(source)
			execution, err := Plan(recipe, catalogue)
			if err != nil {
				test.Fatal(err)
			}
			output, metadata := filepath.Join(root, "plugin"), filepath.Join(root, "metadata.json")
			if err := execution.Write(output, metadata); err != nil {
				test.Fatal(err)
			}
			inventory, err := filemetadata.Read(metadata)
			if err != nil || len(inventory) == 0 {
				test.Fatalf("inventory: %v", err)
			}
			for _, entry := range inventory {
				if entry.RelativePath != "kotlinc" && !strings.HasPrefix(entry.RelativePath, "kotlinc/") {
					test.Fatalf("unowned output: %+v", entry)
				}
				name := strings.TrimPrefix(strings.TrimPrefix(entry.RelativePath, "kotlinc"), "/")
				actual, err := filemetadata.Inspect(filepath.Join(source, name), entry.RelativePath)
				if err != nil || entry != actual {
					test.Fatalf("tree parity: %+v != %+v: %v", entry, actual, err)
				}
			}
			if empty && len(inventory) != 1 {
				test.Fatal("empty tree lost its root")
			}
			if _, err := os.Lstat(filepath.Join(output, "lib")); !os.IsNotExist(err) {
				test.Fatalf("independent output entered remainder: %v", err)
			}
		})
	}
}

func boundaryExecution(test *testing.T, source string, tree bool) *Execution {
	test.Helper()
	recipe, catalogue := treePlan(source)
	if !tree {
		recipe.Version = Version
		recipe.Assets[0].Kind = "file"
		recipe.Operations[0].Kind = "copy"
		recipe.Operations[0].Input.Path = "file"
	}
	execution, err := Plan(recipe, catalogue)
	if err != nil {
		test.Fatal(err)
	}
	return execution
}

// TestRawTreeIsAbsentUntilGoExecution runs the packer on a plan whose raw tree is missing. The packer must refuse
// the plan before any write, and copy the tree with its bytes, modes, directories and links once it exists.
func TestRawTreeIsAbsentUntilGoExecution(test *testing.T) {
	executable := PluginRemainderPackerExecutable(test)
	root := test.TempDir()
	source := filepath.Join(root, "raw-tree")
	arguments := append(projectionContracts(test, filepath.Join(root, "contracts"), true, source),
		"--output-dir=plugin", "--inventory=inventory.json", "--assets=assets.json", "--classpath=plugin-classpath.txt")
	run := func() ([]byte, error) {
		command := exec.Command(executable, arguments...)
		command.Dir = root
		return command.CombinedOutput()
	}
	if output, err := run(); err == nil {
		test.Fatalf("Go accepted the missing raw tree: %s", output)
	}
	for _, output := range []string{"plugin", "inventory.json", "assets.json", "plugin-classpath.txt"} {
		if _, err := os.Lstat(filepath.Join(root, output)); !os.IsNotExist(err) {
			test.Fatalf("Go wrote before raw tree validation: %s: %v", output, err)
		}
	}
	writeTestFile(test, filepath.Join(source, "file"), []byte("prepared later"))
	if err := os.Chmod(filepath.Join(source, "file"), 0o751); err != nil {
		test.Fatal(err)
	}
	if err := os.Mkdir(filepath.Join(source, "empty"), 0o710); err != nil {
		test.Fatal(err)
	}
	if err := os.Symlink("./file", filepath.Join(source, "link")); err != nil {
		test.Fatal(err)
	}
	before, err := filemetadata.Inventory(source)
	if err != nil {
		test.Fatal(err)
	}
	if output, err := run(); err != nil {
		test.Fatalf("Go rejected the supplied raw tree: %v: %s", err, output)
	}
	after, err := filemetadata.Inventory(source)
	if err != nil || !reflect.DeepEqual(before, after) {
		test.Fatalf("Go changed the raw tree: %v", err)
	}
	actual, err := filemetadata.Inventory(filepath.Join(root, "plugin/resources"))
	if err != nil || !reflect.DeepEqual(before, actual) {
		test.Fatalf("raw tree byte, mode, directory or link parity differs: %v", err)
	}
}

func TestFilesystemAliasesCannotPutOutputsInsideInputs(test *testing.T) {
	for _, tree := range []bool{false, true} {
		for _, names := range [][2]string{{"Source", "source"}, {"Caf\u00e9", "Cafe\u0301"}} {
			for _, outputKind := range []string{"payload", "inventory"} {
				test.Run(fmt.Sprintf("tree=%t/%s/%s", tree, names[0], outputKind), func(test *testing.T) {
					root := test.TempDir()
					source, alias := filepath.Join(root, names[0]), filepath.Join(root, names[1])
					writeTestFile(test, filepath.Join(source, "file"), []byte("immutable source"))
					original, err := os.Stat(source)
					if err != nil {
						test.Fatal(err)
					}
					other, err := os.Stat(alias)
					if os.IsNotExist(err) {
						test.Skip("The filesystem distinguishes these source names")
					}
					if err != nil || !os.SameFile(original, other) {
						test.Fatalf("source alias: %v", err)
					}
					execution := boundaryExecution(test, source, tree)
					output, inventory := filepath.Join(root, "output"), filepath.Join(root, "inventory.json")
					if outputKind == "payload" {
						output = filepath.Join(alias, "missing/generated-output")
					} else {
						inventory = filepath.Join(alias, "missing/generated-inventory.json")
					}
					before, err := filemetadata.Inventory(root)
					if err != nil {
						test.Fatal(err)
					}
					if err := execution.Write(output, inventory); err == nil || !strings.Contains(err.Error(), "overlaps input") {
						test.Fatalf("accepted an aliased input boundary: %v", err)
					}
					after, err := filemetadata.Inventory(root)
					if err != nil || !reflect.DeepEqual(before, after) {
						test.Fatalf("boundary failure wrote files: %v", err)
					}
				})
			}
		}
	}
}

func TestOutputAndInventoryReserveAliasedRootsBeforeWriting(test *testing.T) {
	for _, names := range [][2]string{{"Output", "output"}, {"Caf\u00e9", "Cafe\u0301"}} {
		for _, existing := range []bool{false, true} {
			test.Run(fmt.Sprintf("%s/existing=%t", names[0], existing), func(test *testing.T) {
				root := test.TempDir()
				source := filepath.Join(root, "source")
				writeTestFile(test, filepath.Join(source, "file"), []byte("immutable source"))
				output, alias := filepath.Join(root, names[0]), filepath.Join(root, names[1])
				if err := os.Mkdir(output, 0o755); err != nil {
					test.Fatal(err)
				}
				first, err := os.Stat(output)
				if err != nil {
					test.Fatal(err)
				}
				second, err := os.Stat(alias)
				if err != nil && !os.IsNotExist(err) {
					test.Fatal(err)
				}
				aliased := err == nil && os.SameFile(first, second)
				if !existing {
					if err := os.Remove(output); err != nil {
						test.Fatal(err)
					}
				}
				before, err := filemetadata.Inventory(root)
				if err != nil {
					test.Fatal(err)
				}
				err = boundaryExecution(test, source, true).Write(output, filepath.Join(alias, "inventory.json"))
				if !aliased {
					if err != nil {
						test.Fatalf("distinct output names were rejected: %v", err)
					}
					return
				}
				if err == nil || !strings.Contains(err.Error(), "outside the payload") {
					test.Fatalf("accepted aliased output and inventory: %v", err)
				}
				after, err := filemetadata.Inventory(root)
				if err != nil || !reflect.DeepEqual(before, after) {
					test.Fatalf("boundary failure wrote files: %v", err)
				}
			})
		}
	}
}

func TestOutputRootSymlinksRejectTrailingSeparators(test *testing.T) {
	for _, tree := range []bool{false, true} {
		for _, suffix := range []string{"", "/", "//", "/."} {
			test.Run(fmt.Sprintf("tree=%t/suffix=%q", tree, suffix), func(test *testing.T) {
				root := test.TempDir()
				source := filepath.Join(root, "source")
				writeTestFile(test, filepath.Join(source, "file"), []byte("immutable source"))
				if err := os.Mkdir(filepath.Join(root, "target"), 0o755); err != nil {
					test.Fatal(err)
				}
				output := filepath.Join(root, "output")
				if err := os.Symlink("target", output); err != nil {
					test.Fatal(err)
				}
				before, err := filemetadata.Inventory(root)
				if err != nil {
					test.Fatal(err)
				}
				if err := boundaryExecution(test, source, tree).Write(output+suffix, filepath.Join(root, "inventory.json")); err == nil || !strings.Contains(err.Error(), "not a real directory") {
					test.Fatalf("accepted an output root symlink: %v", err)
				}
				after, err := filemetadata.Inventory(root)
				if err != nil || !reflect.DeepEqual(before, after) {
					test.Fatalf("boundary failure wrote files: %v", err)
				}
			})
		}
	}
}

func TestInputRootLinksRejectOutputAliases(test *testing.T) {
	for _, destination := range []string{"payload", "inventory"} {
		test.Run(destination, func(test *testing.T) {
			root := test.TempDir()
			source := filepath.Join(root, "raw")
			writeTestFile(test, filepath.Join(source, "file"), []byte("immutable raw input"))
			alias := filepath.Join(root, "transport")
			if err := os.Symlink("raw", alias); err != nil {
				test.Fatal(err)
			}
			execution := boundaryExecution(test, source, true)
			output, inventory := filepath.Join(root, "output"), filepath.Join(root, "inventory.json")
			if destination == "payload" {
				output = filepath.Join(alias, "nested/output")
			} else {
				inventory = filepath.Join(alias, "nested/inventory.json")
			}
			before, err := filemetadata.Inventory(root)
			if err != nil {
				test.Fatal(err)
			}
			if err := execution.Write(output, inventory); err == nil || !strings.Contains(err.Error(), "overlaps input") {
				test.Fatalf("accepted an output inside an input through a link: %v", err)
			}
			after, err := filemetadata.Inventory(root)
			if err != nil || !reflect.DeepEqual(before, after) {
				test.Fatalf("raw inputs changed: %v", err)
			}
		})
	}
}

func TestFilesystemBoundariesAcceptDistinctSourceNames(test *testing.T) {
	for _, names := range [][2]string{{"Source", "source"}, {"Caf\u00e9", "Cafe\u0301"}} {
		test.Run(names[0], func(test *testing.T) {
			root := test.TempDir()
			source, output := filepath.Join(root, names[0]), filepath.Join(root, names[1])
			writeTestFile(test, filepath.Join(source, "file"), []byte("immutable source"))
			if _, err := os.Stat(output); err == nil {
				test.Skip("The filesystem aliases these names")
			} else if !os.IsNotExist(err) {
				test.Fatal(err)
			}
			before, err := filemetadata.Inventory(source)
			if err != nil {
				test.Fatal(err)
			}
			if err := boundaryExecution(test, source, true).Write(output, filepath.Join(root, "inventory.json")); err != nil {
				test.Fatalf("distinct names were rejected: %v", err)
			}
			after, err := filemetadata.Inventory(source)
			if err != nil || !reflect.DeepEqual(before, after) {
				test.Fatalf("source changed: %v", err)
			}
		})
	}
}

func TestBinaryOutputAliasesPreserveContractFiles(test *testing.T) {
	executable := PluginRemainderPackerExecutable(test)
	for _, tree := range []bool{false, true} {
		for _, names := range [][2]string{{"Contracts", "contracts"}, {"Caf\u00e9", "Cafe\u0301"}} {
			for _, document := range []string{"plan.json", "catalogue.json", "descriptor.xml"} {
				for _, destination := range []string{"payload", "inventory"} {
					test.Run(fmt.Sprintf("tree=%t/%s/%s/%s", tree, names[0], document, destination), func(test *testing.T) {
						root := test.TempDir()
						source := filepath.Join(root, "source")
						writeTestFile(test, filepath.Join(source, "file"), []byte("immutable source"))
						if !tree {
							source = filepath.Join(source, "file")
						}
						contracts, alias := filepath.Join(root, names[0]), filepath.Join(root, names[1])
						arguments := projectionContracts(test, contracts, tree, source)
						if _, err := os.Stat(alias); os.IsNotExist(err) {
							test.Skip("The filesystem distinguishes these contract names")
						} else if err != nil {
							test.Fatal(err)
						}
						output, inventory := filepath.Join(root, "output"), filepath.Join(root, "inventory.json")
						if destination == "payload" {
							output = filepath.Join(alias, document)
						} else {
							inventory = filepath.Join(alias, document)
						}
						before, err := filemetadata.Inventory(root)
						if err != nil {
							test.Fatal(err)
						}
						arguments = append(arguments, "--output-dir="+output, "--inventory="+inventory,
							"--assets="+filepath.Join(root, "assets.json"), "--classpath="+filepath.Join(root, "plugin-classpath.txt"))
						message, err := exec.Command(executable, arguments...).CombinedOutput()
						if err == nil || !strings.Contains(string(message), map[string]string{"payload": "not a real directory", "inventory": "inventory must not exist"}[destination]) {
							test.Fatalf("contract alias was not rejected: %v: %s", err, message)
						}
						after, err := filemetadata.Inventory(root)
						if err != nil || !reflect.DeepEqual(before, after) {
							test.Fatalf("contract or source changed: %v", err)
						}
					})
				}
			}
		}
	}
}

func TestOutputBoundariesRetainParentTransportLinks(test *testing.T) {
	for _, tree := range []bool{false, true} {
		test.Run(fmt.Sprint(tree), func(test *testing.T) {
			root := test.TempDir()
			source := filepath.Join(root, "source")
			writeTestFile(test, filepath.Join(source, "file"), []byte("resource"))
			if err := os.Mkdir(filepath.Join(root, "transport"), 0o755); err != nil {
				test.Fatal(err)
			}
			if err := os.Symlink("transport", filepath.Join(root, "alias")); err != nil {
				test.Fatal(err)
			}
			if err := boundaryExecution(test, source, tree).Write(filepath.Join(root, "alias/output")+"/", filepath.Join(root, "alias/inventory.json")); err != nil {
				test.Fatal(err)
			}
			if _, err := filemetadata.Read(filepath.Join(root, "transport/inventory.json")); err != nil {
				test.Fatal(err)
			}
		})
	}
}

func TestCopyTreeMaterializesBazelTransportFilesAndKeepsPayloadLinks(test *testing.T) {
	root := test.TempDir()
	backing := filepath.Join(root, "backing")
	license := filepath.Join(backing, "adoc/LICENSE")
	writeTestFile(test, license, []byte("license text"))
	if err := os.Chmod(license, 0o751); err != nil {
		test.Fatal(err)
	}
	transport := filepath.Join(root, "transport")
	if err := os.MkdirAll(filepath.Join(transport, "adoc"), 0o755); err != nil {
		test.Fatal(err)
	}
	if err := os.Symlink(license, filepath.Join(transport, "adoc/LICENSE")); err != nil {
		test.Fatal(err)
	}
	if err := os.Symlink("adoc/LICENSE", filepath.Join(transport, "current")); err != nil {
		test.Fatal(err)
	}
	recipe, catalogue := treePlan(transport)
	execution, err := Plan(recipe, catalogue)
	if err != nil {
		test.Fatal(err)
	}
	output, inventory := filepath.Join(root, "output"), filepath.Join(root, "inventory.json")
	if err := execution.Write(output, inventory); err != nil {
		test.Fatal(err)
	}
	materialized := filepath.Join(output, "kotlinc/adoc/LICENSE")
	info, err := os.Lstat(materialized)
	if err != nil || !info.Mode().IsRegular() || info.Mode().Perm() != 0o751 || string(readTestFile(test, materialized)) != "license text" {
		test.Fatalf("transport file was not materialized: %v: %v", info, err)
	}
	link := filepath.Join(output, "kotlinc/current")
	if target, err := os.Readlink(link); err != nil || target != "adoc/LICENSE" {
		test.Fatalf("payload link changed: %q: %v", target, err)
	}
	entries, err := filemetadata.Read(inventory)
	if err != nil {
		test.Fatal(err)
	}
	for _, entry := range entries {
		if entry.RelativePath == "kotlinc/adoc/LICENSE" && entry.Type != "file" {
			test.Fatalf("transport file entered the inventory as %q", entry.Type)
		}
	}
}

func TestCopyTreeCopiesIntoPluginRoot(test *testing.T) {
	source := filepath.Join(test.TempDir(), "source")
	writeTestFile(test, filepath.Join(source, "bin/tool"), []byte("tool"))
	recipe, catalogue := treePlan(source)
	recipe.Assets[0].Destination = ""
	recipe.Operations[0].Destination = ""
	execution, err := Plan(recipe, catalogue)
	if err != nil {
		test.Fatal(err)
	}
	root := test.TempDir()
	output, inventory := filepath.Join(root, "output"), filepath.Join(root, "inventory.json")
	if err := execution.Write(output, inventory); err != nil {
		test.Fatal(err)
	}
	if !bytes.Equal(readTestFile(test, filepath.Join(output, "bin/tool")), readTestFile(test, filepath.Join(source, "bin/tool"))) {
		test.Fatal("plugin-root tree payload differs")
	}
	entries, err := filemetadata.Read(inventory)
	if err != nil {
		test.Fatal(err)
	}
	if slices.ContainsFunc(entries, func(entry filemetadata.Entry) bool { return entry.RelativePath == "" }) {
		test.Fatal("the plugin root entered the inventory")
	}
}

func TestCopyTreeRejectsDeclaredDescendantCollisionBeforeWriting(test *testing.T) {
	root := test.TempDir()
	source := filepath.Join(root, "source")
	writeTestFile(test, filepath.Join(source, "lib/compiler.jar"), []byte("tree content"))
	recipe, catalogue := treePlan(source)
	recipe.Assets[1] = Asset{Destination: "kotlinc/lib/compiler.jar", Producer: "remainder"}
	recipe.Operations = append(recipe.Operations, Operation{
		Kind: "jar", Destination: "kotlinc/lib/compiler.jar", Options: &JarOptions{Directories: "none"},
	})
	execution, err := Plan(recipe, catalogue)
	if err != nil {
		test.Fatal(err)
	}
	if err := execution.Write(filepath.Join(root, "output"), filepath.Join(root, "inventory.json")); err == nil || !strings.Contains(err.Error(), "conflicting output destination") {
		test.Fatalf("expected a resolved tree collision, got %v", err)
	}
	assertNoPublishedOutputs(test, root)
}

func TestCopyTreeRejectsUnsafeBazelTransportFilesBeforeWriting(test *testing.T) {
	for _, scenario := range []struct {
		name    string
		prepare func(*testing.T, string, string)
	}{
		{"path mismatch", func(test *testing.T, root, transport string) {
			target := filepath.Join(root, "backing/other/LICENSE")
			writeTestFile(test, target, []byte("license"))
			if err := os.MkdirAll(filepath.Join(transport, "adoc"), 0o755); err != nil {
				test.Fatal(err)
			}
			if err := os.Symlink(target, filepath.Join(transport, "adoc/LICENSE")); err != nil {
				test.Fatal(err)
			}
		}},
		{"conflicting roots", func(test *testing.T, root, transport string) {
			for _, name := range []string{"adoc/LICENSE", "css/LICENSE"} {
				target := filepath.Join(root, filepath.Dir(name), filepath.FromSlash(name))
				writeTestFile(test, target, []byte(name))
				if err := os.MkdirAll(filepath.Join(transport, filepath.Dir(name)), 0o755); err != nil {
					test.Fatal(err)
				}
				if err := os.Symlink(target, filepath.Join(transport, filepath.FromSlash(name))); err != nil {
					test.Fatal(err)
				}
			}
		}},
		{"linked parent", func(test *testing.T, root, transport string) {
			realParent := filepath.Join(root, "real/adoc")
			writeTestFile(test, filepath.Join(realParent, "LICENSE"), []byte("license"))
			backing := filepath.Join(root, "backing")
			if err := os.Mkdir(backing, 0o755); err != nil {
				test.Fatal(err)
			}
			if err := os.Symlink(realParent, filepath.Join(backing, "adoc")); err != nil {
				test.Fatal(err)
			}
			if err := os.MkdirAll(filepath.Join(transport, "adoc"), 0o755); err != nil {
				test.Fatal(err)
			}
			if err := os.Symlink(filepath.Join(backing, "adoc/LICENSE"), filepath.Join(transport, "adoc/LICENSE")); err != nil {
				test.Fatal(err)
			}
		}},
	} {
		test.Run(scenario.name, func(test *testing.T) {
			root := test.TempDir()
			transport := filepath.Join(root, "transport")
			if err := os.Mkdir(transport, 0o755); err != nil {
				test.Fatal(err)
			}
			scenario.prepare(test, root, transport)
			recipe, catalogue := treePlan(transport)
			execution, err := Plan(recipe, catalogue)
			if err != nil {
				test.Fatal(err)
			}
			if err := execution.Write(filepath.Join(root, "output"), filepath.Join(root, "inventory.json")); err == nil {
				test.Fatal("accepted an unsafe transport tree")
			}
			assertNoPublishedOutputs(test, root)
		})
	}
}

func TestCopyTreeRejectsUnsafeEntriesBeforeWriting(test *testing.T) {
	for _, scenario := range []struct {
		name   string
		mutate func(string) error
	}{
		{"missing", func(source string) error { return os.Remove(source) }},
		{"root link", func(source string) error {
			if err := os.Remove(source); err != nil {
				return err
			}
			return os.Symlink(".", source)
		}},
		{"root file", func(source string) error {
			if err := os.Remove(source); err != nil {
				return err
			}
			return os.WriteFile(source, []byte("file"), 0o644)
		}},
		{"missing link", func(source string) error { return os.Symlink("missing", filepath.Join(source, "link")) }},
		{"escaping link", func(source string) error { return os.Symlink("../outside", filepath.Join(source, "link")) }},
		{"absolute link", func(source string) error { return os.Symlink(source, filepath.Join(source, "link")) }},
		{"directory cycle", func(source string) error { return os.Symlink(".", filepath.Join(source, "link")) }},
		{"file traversal", func(source string) error {
			if err := os.WriteFile(filepath.Join(source, "file"), nil, 0o644); err != nil {
				return err
			}
			return os.Symlink("file/../file", filepath.Join(source, "link"))
		}},
		{"hard link", func(source string) error {
			if err := os.WriteFile(filepath.Join(source, "file"), nil, 0o644); err != nil {
				return err
			}
			return os.Link(filepath.Join(source, "file"), filepath.Join(source, "alias"))
		}},
		{"case alias", func(source string) error {
			if err := os.WriteFile(filepath.Join(source, "file"), nil, 0o644); err != nil {
				return err
			}
			return os.Symlink("FILE", filepath.Join(source, "alias"))
		}},
		{"special file", func(source string) error { return exec.Command("mkfifo", filepath.Join(source, "pipe")).Run() }},
		{"unsafe name", func(source string) error { return os.WriteFile(filepath.Join(source, "bad:name"), nil, 0o644) }},
		{"nested collision", func(source string) error {
			if err := os.Mkdir(filepath.Join(source, "nested"), 0o755); err != nil {
				return err
			}
			if err := os.WriteFile(filepath.Join(source, "nested/Straße"), nil, 0o644); err != nil {
				return err
			}
			file, err := os.OpenFile(filepath.Join(source, "nested/STRASSE"), os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o644)
			if err != nil {
				return err
			}
			return file.Close()
		}},
		{"link cycle", func(source string) error {
			if err := os.Symlink("second", filepath.Join(source, "first")); err != nil {
				return err
			}
			return os.Symlink("first", filepath.Join(source, "second"))
		}},
	} {
		test.Run(scenario.name, func(test *testing.T) {
			root := test.TempDir()
			source := filepath.Join(root, "source")
			if err := os.Mkdir(source, 0o755); err != nil {
				test.Fatal(err)
			}
			recipe, catalogue := treePlan(source)
			execution, err := Plan(recipe, catalogue)
			if err != nil {
				test.Fatal(err)
			}
			if err := scenario.mutate(source); err != nil {
				if scenario.name == "nested collision" && os.IsExist(err) {
					test.Skip("The filesystem prevents two aliased source names")
				}
				test.Fatal(err)
			}
			output, metadata := filepath.Join(root, "plugin"), filepath.Join(root, "metadata.json")
			if err := execution.Write(output, metadata); err == nil {
				test.Fatal("accepted unsafe tree")
			}
			for _, file := range []string{output, metadata} {
				if _, err := os.Lstat(file); !os.IsNotExist(err) {
					test.Fatalf("wrote output before tree validation: %s: %v", file, err)
				}
			}
		})
	}
}

func TestExplicitDirectoriesPreserveModesWithoutIndependentPayloads(test *testing.T) {
	root := test.TempDir()
	assets := []Asset{
		{Destination: "resources/empty", Producer: "remainder", Kind: "directory"},
		{Destination: "resources", Producer: "remainder", Kind: "directory"},
		{Destination: "lib/shared.jar", Producer: "independent", Artifact: "unbuilt"},
		{Destination: "current", Producer: "remainder"},
	}
	recipe := Recipe{Version: Version, Plugin: "directories", LayoutSignature: "directories-v1", Assets: assets, Operations: []Operation{
		{Kind: "directory", Destination: "resources/empty", Mode: 0o710},
		{Kind: "directory", Destination: "resources", Mode: 0o500},
		{Kind: "symlink", Destination: "current", Target: "resources/empty"},
	}}
	execution, err := Plan(recipe, Catalogue{Version: Version})
	if err != nil {
		test.Fatal(err)
	}
	output := filepath.Join(root, "plugin")
	test.Cleanup(func() { os.Chmod(filepath.Join(output, "resources"), 0o755) })
	metadata := filepath.Join(root, "inventory.json")
	if err := execution.Write(output, metadata); err != nil {
		test.Fatal(err)
	}
	entries, err := filemetadata.Read(metadata)
	if err != nil || len(entries) != 3 {
		test.Fatalf("inventory: %+v: %v", entries, err)
	}
	for _, entry := range entries {
		if entry.Type == "directory" {
			info, err := os.Lstat(filepath.Join(output, entry.RelativePath))
			if err != nil || !info.IsDir() || uint32(info.Mode().Perm()) != entry.Mode || entry.Hash != 0 || entry.Executable {
				test.Fatalf("directory: %+v: %v", entry, err)
			}
		}
	}
	if entries[1].Mode != 0o500 || entries[2].Mode != 0o710 {
		test.Fatalf("directory modes: %+v", entries)
	}
	if _, err := os.Lstat(filepath.Join(output, "lib")); !os.IsNotExist(err) {
		test.Fatalf("independent tree was materialized: %v", err)
	}
	if err := execution.Write(output, filepath.Join(root, "stale.json")); err == nil {
		test.Fatal("accepted stale directories")
	}
}

type testEntry struct {
	name string
	data string
}

func archiveFile(t *testing.T, file string, entries ...testEntry) {
	t.Helper()
	var buffer bytes.Buffer
	writer := zip.NewWriter(&buffer)
	for _, entry := range entries {
		output, err := writer.Create(entry.name)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := io.WriteString(output, entry.data); err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	writeTestFile(t, file, buffer.Bytes())
}

func writeTestFile(t *testing.T, file string, data []byte) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(file), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(file, data, 0o644); err != nil {
		t.Fatal(err)
	}
}

func readTestFile(t *testing.T, file string) []byte {
	t.Helper()
	data, err := os.ReadFile(file)
	if err != nil {
		t.Fatal(err)
	}
	return data
}

func readArchive(t *testing.T, file string) ([]string, map[string]string) {
	t.Helper()
	archive, err := zip.OpenReader(file)
	if err != nil {
		t.Fatal(err)
	}
	defer archive.Close()
	var names []string
	entries := make(map[string]string)
	for _, entry := range archive.File {
		names = append(names, entry.Name)
		input, err := entry.Open()
		if err != nil {
			t.Fatal(err)
		}
		data, err := io.ReadAll(input)
		input.Close()
		if err != nil {
			t.Fatal(err)
		}
		entries[entry.Name] = string(data)
	}
	return names, entries
}

func writeExecution(t *testing.T, recipe Recipe, catalogue Catalogue) (string, []filemetadata.Entry) {
	t.Helper()
	execution, err := Plan(recipe, catalogue)
	if err != nil {
		t.Fatal(err)
	}
	root := t.TempDir()
	output, inventory := filepath.Join(root, "plugin"), filepath.Join(root, "inventory.json")
	if err := execution.Write(output, inventory); err != nil {
		t.Fatal(err)
	}
	record, err := filemetadata.Read(inventory)
	if err != nil {
		t.Fatal(err)
	}
	return output, record
}

func TestRelativeDirectoryRootsResolveThroughWorkingDirectoryLinks(test *testing.T) {
	root := test.TempDir()
	physical := filepath.Join(root, "physical")
	archiveFile(test, filepath.Join(physical, "entries", "input.jar"), testEntry{"Service.class", "class bytes"})
	alias := filepath.Join(root, "alias")
	if err := os.Symlink(physical, alias); err != nil {
		test.Skipf("symbolic links are unavailable: %v", err)
	}
	test.Chdir(alias)
	test.Setenv("PWD", alias)
	recipe, catalogue := samplePlan(physical)
	catalogue.Artifacts[0].Kind = "directory"
	catalogue.Artifacts[0].Root = "entries"
	recipe.Operations[0].Sources[0].Input.Path = "input.jar"
	output, _ := writeExecution(test, recipe, catalogue)
	_, contents := readArchive(test, filepath.Join(output, "lib/plugin.jar"))
	if contents["Service.class"] != "class bytes" {
		test.Fatalf("relative input lost its content: %v", contents)
	}
}

func TestBatchWritesOnlyItsAssetsInLayoutOrder(t *testing.T) {
	root := t.TempDir()
	recipe, catalogue := samplePlan(root)
	archiveFile(t, filepath.Join(root, "module.jar"),
		testEntry{"META-INF/plugin.xml", "original descriptor"},
		testEntry{"spring/security/Mvc.class", "shared content module"},
		testEntry{"native/lib.so", "unsigned"},
		testEntry{"native/extracted.so", "extract me"},
		testEntry{"module/After.class", "after native"},
		testEntry{"META-INF/listOfEntities.txt", " Module "},
		testEntry{"icon-robots.txt", "excluded"},
	)
	archiveFile(t, filepath.Join(root, "first.jar"),
		testEntry{"first/Library.class", "first library"}, testEntry{"shared.txt", "first wins"},
		testEntry{"META-INF/listOfEntities.txt", " First "}, testEntry{"LICENSE", "excluded"},
	)
	archiveFile(t, filepath.Join(root, "second.jar"),
		testEntry{"second/Library.class", "second library"}, testEntry{"shared.txt", "second loses"},
		testEntry{"META-INF/listOfEntities.txt", " Second "},
	)
	prepared := filepath.Join(root, "prepared")
	for name, content := range map[string]string{"plugin.xml": "patched descriptor", "signed.so": "signed native", "custom": "custom filter result", "entities": " Prepared "} {
		writeTestFile(t, filepath.Join(prepared, name), []byte(content))
	}
	catalogue.Artifacts = append(catalogue.Artifacts,
		Artifact{ID: "first", Kind: "file", Root: filepath.Join(root, "first.jar")},
		Artifact{ID: "second", Kind: "file", Root: filepath.Join(root, "second.jar")},
		Artifact{ID: "prepared", Kind: "directory", Root: prepared},
	)
	module := recipe.Operations[0].Sources[0]
	recipe.Operations[0].Sources = []Source{
		{Kind: "entries", Manifest: "keep", Entries: []PreparedEntry{{Kind: "patch", Name: "META-INF/plugin.xml", Input: &Reference{Artifact: "prepared", Path: "plugin.xml"}}}},
		{Kind: "archive", Input: &Reference{Artifact: "first"}, Filter: "library", Manifest: "drop"},
		{Kind: "archive", Input: &Reference{Artifact: "second"}, Filter: "library", Manifest: "drop"},
		module, module,
		{Kind: "entries", Manifest: "drop", Entries: []PreparedEntry{
			{Kind: "file", Name: "custom/selected.txt", Input: &Reference{Artifact: "prepared", Path: "custom"}},
			{Kind: "file", Name: "META-INF/listOfEntities.txt", Input: &Reference{Artifact: "prepared", Path: "entities"}},
		}},
	}
	recipe.Assets = append(recipe.Assets,
		Asset{Destination: "lib/nested/custom.jar", Producer: "remainder"},
		Asset{Destination: "lib/first-library.jar", Producer: "remainder"},
		Asset{Destination: "bin/native", Producer: "remainder"},
		Asset{Destination: "lib/alias.jar", Producer: "remainder"},
	)
	recipe.Operations = append(recipe.Operations,
		Operation{Kind: "symlink", Destination: "lib/alias.jar", Target: "./modules/../modules/separate.jar"},
		Operation{Kind: "copy", Destination: "bin/native", Input: &Reference{Artifact: "prepared", Path: "signed.so"}, Mode: 0o755},
		Operation{Kind: "jar", Destination: "lib/first-library.jar", Options: &JarOptions{Directories: "none"}, Sources: []Source{{Kind: "archive", Input: &Reference{Artifact: "first"}, Filter: "library", Manifest: "keep"}}},
		Operation{Kind: "jar", Destination: "lib/nested/custom.jar", Options: &JarOptions{Directories: "all"}, Sources: []Source{{Kind: "entries", Manifest: "keep", Entries: []PreparedEntry{{Kind: "file", Name: "custom/Value.class", Input: &Reference{Artifact: "prepared", Path: "custom"}}}}}},
	)
	output, inventory := writeExecution(t, recipe, catalogue)
	if _, err := os.Lstat(filepath.Join(output, "lib/modules/separate.jar")); !os.IsNotExist(err) {
		t.Fatal("the independent asset reached the remainder output")
	}
	var paths []string
	for _, file := range inventory {
		paths = append(paths, file.RelativePath)
		actual, err := filemetadata.Inspect(filepath.Join(output, filepath.FromSlash(file.RelativePath)), file.RelativePath)
		if err != nil || actual != file {
			t.Fatalf("inventory differs from its output: %+v, %+v: %v", file, actual, err)
		}
	}
	wantPaths := []string{"bin/native", "lib/alias.jar", "lib/first-library.jar", "lib/nested/custom.jar", "lib/plugin.jar"}
	if !slices.Equal(paths, wantPaths) {
		t.Fatalf("inventory order: %v", paths)
	}
	if inventory[0].Mode != 0o755 || inventory[1].Type != "symlink" || inventory[1].SymlinkTarget != "./modules/../modules/separate.jar" {
		t.Fatalf("lost modes or link spelling: %+v", inventory)
	}
	if target, err := os.Readlink(filepath.Join(output, "lib/alias.jar")); err != nil || target != inventory[1].SymlinkTarget {
		t.Fatalf("link target %q: %v", target, err)
	}
	names, entries := readArchive(t, filepath.Join(output, "lib/plugin.jar"))
	wantNames := []string{"META-INF/plugin.xml", "first/Library.class", "shared.txt", "second/Library.class", "spring/security/Mvc.class", "native/lib.so", "native/extracted.so", "module/After.class", "custom/selected.txt", "META-INF/listOfEntities.txt", "__index__"}
	if !slices.Equal(names, wantNames) {
		t.Fatalf("source order changed:\n%v\nwant %v", names, wantNames)
	}
	if entries["META-INF/plugin.xml"] != "patched descriptor" || entries["native/lib.so"] != "unsigned" || entries["shared.txt"] != "first wins" || entries["META-INF/listOfEntities.txt"] != "First\nSecond\nModule\nModule\nPrepared" {
		t.Fatalf("changed writer semantics: %v", entries)
	}
	customNames, _ := readArchive(t, filepath.Join(output, "lib/nested/custom.jar"))
	if !slices.Equal(customNames, []string{"custom/Value.class", "custom/", "__index__"}) {
		t.Fatalf("test plugin directory entries: %v", customNames)
	}
	reference := filepath.Join(t.TempDir(), "library.jar")
	if _, err := (jarpack.MergeSpec{Output: reference, KeepManifest: true, Sources: []jarpack.Source{{Path: filepath.Join(root, "first.jar"), Filter: jarpack.LibraryNameFilter}}}).Pack(); err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(readTestFile(t, reference), readTestFile(t, filepath.Join(output, "lib/first-library.jar"))) {
		t.Fatal("the batch writer changed ordinary jar bytes")
	}
}

func TestFailedMergePublishesNothing(t *testing.T) {
	for _, name := range []string{"missing input", "late patch", "conflicting patches", "unsafe archive name", "unsafe prepared entry"} {
		t.Run(name, func(t *testing.T) {
			root := t.TempDir()
			recipe, catalogue := samplePlan(root)
			archiveFile(t, filepath.Join(root, "module.jar"), testEntry{"entry.txt", "original"})
			switch name {
			case "missing input":
				catalogue.Artifacts[0].Root = filepath.Join(root, "missing.jar")
			case "late patch", "conflicting patches":
				patch := Source{Kind: "entries", Manifest: "keep", Entries: []PreparedEntry{{Kind: "patch", Name: "entry.txt", Input: &Reference{Artifact: "module"}}}}
				if name == "late patch" {
					recipe.Operations[0].Sources = append(recipe.Operations[0].Sources, patch)
				} else {
					recipe.Operations[0].Sources = []Source{patch, patch}
				}
			case "unsafe archive name":
				archiveFile(t, filepath.Join(root, "module.jar"), testEntry{"../outside", "bad"})
			case "unsafe prepared entry":
				recipe.Operations[0].Sources = []Source{{Kind: "entries", Manifest: "drop", Entries: []PreparedEntry{{Kind: "file", Name: "../outside", Input: &Reference{Artifact: "module"}}}}}
			}
			output, inventory := filepath.Join(root, "output"), filepath.Join(root, "inventory.json")
			execution, err := Plan(recipe, catalogue)
			if err == nil {
				err = execution.Write(output, inventory)
			}
			if err == nil {
				t.Fatal("accepted an invalid merge")
			}
			for _, file := range []string{output, inventory, filepath.Join(root, "outside")} {
				if _, err := os.Lstat(file); !os.IsNotExist(err) {
					t.Fatalf("published partial output %s", file)
				}
			}
			stages, err := filepath.Glob(filepath.Join(root, ".plugin-*"))
			if err != nil || len(stages) != 0 {
				t.Fatalf("left staging files %v: %v", stages, err)
			}
		})
	}
}

func TestDirectoryBoundaryAndDeclaredRootSymlinks(t *testing.T) {
	for _, escape := range []bool{false, true} {
		t.Run(map[bool]string{false: "inside", true: "escape"}[escape], func(t *testing.T) {
			root := t.TempDir()
			recipe, catalogue := samplePlan(root)
			declared := filepath.Join(root, "declared")
			archiveFile(t, filepath.Join(declared, "module.jar"), testEntry{"file", "content"})
			catalogue.Artifacts[0].Kind = "directory"
			catalogue.Artifacts[0].Root = filepath.Join(root, "bazel-root-link")
			if err := os.Symlink(declared, catalogue.Artifacts[0].Root); err != nil {
				t.Fatal(err)
			}
			target := "module.jar"
			if escape {
				archiveFile(t, filepath.Join(root, "outside.jar"), testEntry{"file", "outside"})
				target = "../outside.jar"
			}
			if err := os.Symlink(target, filepath.Join(declared, "input.jar")); err != nil {
				t.Fatal(err)
			}
			recipe.Operations[0].Sources[0].Input.Path = "input.jar"
			execution, err := Plan(recipe, catalogue)
			if err != nil {
				t.Fatal(err)
			}
			err = execution.Write(filepath.Join(root, "output"), filepath.Join(root, "inventory.json"))
			if escape && (err == nil || !strings.Contains(err.Error(), "escapes")) || !escape && err != nil {
				t.Fatalf("escape=%v: %v", escape, err)
			}
		})
	}
}

func TestOutputBoundariesAndExistingOutputs(t *testing.T) {
	for _, scenario := range []string{"inventory inside output", "output inside input", "output symlink", "nonempty output", "existing inventory", "empty output"} {
		t.Run(scenario, func(t *testing.T) {
			root := t.TempDir()
			recipe, catalogue := samplePlan(root)
			archiveFile(t, filepath.Join(root, "module.jar"), testEntry{"file", "content"})
			output, inventory := filepath.Join(root, "output"), filepath.Join(root, "inventory.json")
			switch scenario {
			case "inventory inside output":
				inventory = filepath.Join(output, "inventory.json")
			case "output inside input":
				catalogue.Artifacts[0].Kind, catalogue.Artifacts[0].Root = "directory", root
				recipe.Operations[0].Sources[0].Input.Path = "module.jar"
			case "output symlink":
				if err := os.Symlink(root, output); err != nil {
					t.Fatal(err)
				}
			case "nonempty output":
				writeTestFile(t, filepath.Join(output, "keep"), []byte("keep"))
			case "existing inventory":
				writeTestFile(t, inventory, []byte("keep"))
			case "empty output":
				if err := os.Mkdir(output, 0o755); err != nil {
					t.Fatal(err)
				}
			}
			execution, err := Plan(recipe, catalogue)
			if err != nil {
				t.Fatal(err)
			}
			err = execution.Write(output, inventory)
			if (err == nil) != (scenario == "empty output") {
				t.Fatalf("unexpected result: %v", err)
			}
			if scenario == "nonempty output" && string(readTestFile(t, filepath.Join(output, "keep"))) != "keep" {
				t.Fatal("changed an existing output")
			}
		})
	}
}

func TestEmptyRemainderAndEmptyJar(t *testing.T) {
	for _, jar := range []bool{false, true} {
		recipe, catalogue := samplePlan(t.TempDir())
		catalogue.Artifacts = nil
		if jar {
			recipe.Operations[0].Sources = nil
		} else {
			recipe.Operations = nil
			recipe.Assets = recipe.Assets[:1]
		}
		output, inventory := writeExecution(t, recipe, catalogue)
		if jar {
			names, _ := readArchive(t, filepath.Join(output, "lib/plugin.jar"))
			if len(names) != 0 || len(inventory) != 1 {
				t.Fatal("expected one empty jar")
			}
		} else if !reflect.DeepEqual(inventory, []filemetadata.Entry{}) {
			t.Fatalf("expected an empty inventory: %#v", inventory)
		}
		data, err := json.Marshal(inventory)
		if err != nil || !json.Valid(data) {
			t.Fatalf("invalid inventory: %s: %v", data, err)
		}
	}
}

func TestNativeFrameworkLinksKeepExactSpelling(t *testing.T) {
	for _, currentTarget := range []string{"A", "./A", "A/", "A//", "./A///", "A/./"} {
		t.Run(currentTarget, func(t *testing.T) {
			root := t.TempDir()
			binary := filepath.Join(root, "binary")
			header := filepath.Join(root, "header")
			writeTestFile(t, binary, []byte("native binary"))
			writeTestFile(t, header, []byte("native header"))
			operations := []Operation{
				symbolicLink("Framework.framework/Framework", "Versions//Current/Framework"),
				symbolicLink("Framework.framework/Headers", "Versions/Current//Headers/"),
				symbolicLink("Framework.framework/Versions/Current", currentTarget),
				{Kind: "copy", Destination: "Framework.framework/Versions/A/Framework", Input: &Reference{Artifact: "binary"}, Mode: 493},
				{Kind: "copy", Destination: "Framework.framework/Versions/A/Headers/header.h", Input: &Reference{Artifact: "header"}, Mode: 420},
			}
			recipe, catalogue := linkPlan(nil, operations...)
			catalogue.Artifacts = []Artifact{{ID: "binary", Kind: "file", Root: binary}, {ID: "header", Kind: "file", Root: header}}
			output, inventory := writeExecution(t, recipe, catalogue)
			for _, operation := range operations {
				if operation.Kind != "symlink" {
					continue
				}
				target, err := os.Readlink(filepath.Join(output, filepath.FromSlash(operation.Destination)))
				if err != nil || target != operation.Target {
					t.Fatalf("link spelling changed from %q to %q: %v", operation.Target, target, err)
				}
			}
			if got := string(readTestFile(t, filepath.Join(output, "Framework.framework/Framework"))); got != "native binary" {
				t.Fatalf("framework binary is %q", got)
			}
			if got := string(readTestFile(t, filepath.Join(output, "Framework.framework/Headers/header.h"))); got != "native header" {
				t.Fatalf("framework header is %q", got)
			}
			for _, entry := range inventory {
				actual, err := filemetadata.Inspect(filepath.Join(output, filepath.FromSlash(entry.RelativePath)), entry.RelativePath)
				if err != nil || actual != entry {
					t.Fatalf("inventory differs from framework output: %+v, %+v: %v", entry, actual, err)
				}
			}
			info, err := os.Stat(filepath.Join(output, "Framework.framework/Framework"))
			if err != nil || info.Mode().Perm() != 0o755 {
				t.Fatalf("lost executable mode: %v: %v", info, err)
			}
		})
	}
}

func TestDirectoryLinksDoNotMaterializeIndependentAssets(t *testing.T) {
	recipe, catalogue := linkPlan([]string{"Versions/A/independent.jar"}, symbolicLink("Versions/Current", "A/"))
	output, inventory := writeExecution(t, recipe, catalogue)
	if len(inventory) != 1 || inventory[0].RelativePath != "Versions/Current" || inventory[0].SymlinkTarget != "A/" {
		t.Fatalf("unexpected inventory: %+v", inventory)
	}
	if _, err := os.Lstat(filepath.Join(output, "Versions/A")); !os.IsNotExist(err) {
		t.Fatalf("materialized the independent target directory: %v", err)
	}
}

func TestWriteRejectsUnicodeAliasesBeforeOpeningJarInputs(t *testing.T) {
	requireUnicodeAliases(t)
	for _, test := range []struct {
		name         string
		destinations []string
	}{
		{"reviewer file aliases", []string{"lib/caf\u00e9.jar", "lib/cafe\u0301.jar"}},
		{"directory aliases", []string{"lib/caf\u00e9/first.jar", "lib/cafe\u0301/second.jar"}},
		{"file then directory", []string{"lib/caf\u00e9", "lib/cafe\u0301/second.jar"}},
		{"directory then file", []string{"lib/cafe\u0301/first.jar", "lib/caf\u00e9"}},
	} {
		t.Run(test.name, func(t *testing.T) {
			root := t.TempDir()
			recipe, catalogue := samplePlan(root)
			writeTestFile(t, catalogue.Artifacts[0].Root, []byte("invalid jar: reservations must fail before this input is opened"))
			operation := recipe.Operations[0]
			recipe.Assets, recipe.Operations = nil, nil
			for _, destination := range test.destinations {
				recipe.Assets = append(recipe.Assets, Asset{Destination: destination, Producer: "remainder"})
				operation.Destination = destination
				recipe.Operations = append(recipe.Operations, operation)
			}
			execution, err := Plan(recipe, catalogue)
			if err != nil {
				t.Fatal(err)
			}
			output, inventory := filepath.Join(root, "output"), filepath.Join(root, "inventory.json")
			err = execution.Write(output, inventory)
			if err == nil || !strings.Contains(err.Error(), "conflicting output") {
				t.Fatalf("expected an exclusive reservation failure before merging, got %v", err)
			}
			assertNoPublishedOutputs(t, root)
		})
	}
}

func requireUnicodeAliases(t *testing.T) {
	t.Helper()
	probe := t.TempDir()
	writeTestFile(t, filepath.Join(probe, "caf\u00e9"), nil)
	if _, err := os.Lstat(filepath.Join(probe, "cafe\u0301")); os.IsNotExist(err) {
		t.Skip("the filesystem does not alias NFC and NFD names")
	} else if err != nil {
		t.Fatal(err)
	}
}

func TestUnicodeAliasesCannotHideIndependentTargetsOrLinks(t *testing.T) {
	for _, independent := range []bool{false, true} {
		for _, reverse := range []bool{false, true} {
			root := t.TempDir()
			file := "caf\u00e9.jar"
			alias := symbolicLink("cafe\u0301.jar", file)
			var recipe Recipe
			var catalogue Catalogue
			if independent {
				recipe, catalogue = linkPlan([]string{file}, alias)
			} else {
				recipe, catalogue = linkPlan(nil, alias, Operation{Kind: "jar", Destination: file, Options: &JarOptions{Directories: "none"}})
			}
			if reverse {
				slices.Reverse(recipe.Operations)
				slices.Reverse(recipe.Assets)
			}
			execution, err := Plan(recipe, catalogue)
			if err != nil {
				if !strings.Contains(err.Error(), "symbolic link cycle") {
					t.Fatal(err)
				}
				assertNoPublishedOutputs(t, root)
				continue
			}
			requireUnicodeAliases(t)
			err = execution.Write(filepath.Join(root, "output"), filepath.Join(root, "inventory.json"))
			if err == nil || !strings.Contains(err.Error(), "conflicting output") {
				t.Fatalf("independent=%v, reverse=%v: expected a collision, got %v", independent, reverse, err)
			}
			assertNoPublishedOutputs(t, root)
		}
	}
}

func TestWriteRejectsUnicodeAliasesAcrossIndependentAssets(t *testing.T) {
	requireUnicodeAliases(t)
	for _, test := range []struct {
		name        string
		independent string
		remainder   string
	}{
		{"file aliases", "lib/caf\u00e9.jar", "lib/cafe\u0301.jar"},
		{"directory aliases", "lib/caf\u00e9/independent.jar", "lib/cafe\u0301/remainder.jar"},
		{"file then directory", "lib/caf\u00e9", "lib/cafe\u0301/remainder.jar"},
		{"directory then file", "lib/cafe\u0301/independent.jar", "lib/caf\u00e9"},
	} {
		t.Run(test.name, func(t *testing.T) {
			for _, reverse := range []bool{false, true} {
				root := t.TempDir()
				recipe, catalogue := samplePlan(root)
				writeTestFile(t, catalogue.Artifacts[0].Root, []byte("invalid jar: reservations must fail before this input is opened"))
				recipe.Assets[0].Destination = test.independent
				recipe.Assets[1].Destination = test.remainder
				recipe.Operations[0].Destination = test.remainder
				if reverse {
					slices.Reverse(recipe.Assets)
				}
				execution, err := Plan(recipe, catalogue)
				if err != nil {
					t.Fatal(err)
				}
				err = execution.Write(filepath.Join(root, "output"), filepath.Join(root, "inventory.json"))
				if err == nil || !strings.Contains(err.Error(), "conflicting output") {
					t.Fatalf("reverse=%v: expected an exclusive reservation failure before merging, got %v", reverse, err)
				}
				assertNoPublishedOutputs(t, root)
			}
		})
	}
}

func assertNoPublishedOutputs(t *testing.T, root string) {
	t.Helper()
	for _, name := range []string{"output", "inventory.json"} {
		if _, err := os.Lstat(filepath.Join(root, name)); !os.IsNotExist(err) {
			t.Fatalf("published partial output %s: %v", name, err)
		}
	}
	stages, err := filepath.Glob(filepath.Join(root, ".plugin-*"))
	if err != nil || len(stages) != 0 {
		t.Fatalf("left reservation files %v: %v", stages, err)
	}
}

// moduleExcludesFixture is one module jar in a deliberate central-directory order.
// The Kotlin preparer reads a module jar in this order, and so does jarpack.
var moduleExcludesFixture = []testEntry{
	{"drop/Ignore.class", "excluded by the glob"},
	{"keep/Service.class", "kept"},
	{"META-INF/listOfEntities.txt", " Module "},
	{"META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"},
	{"icon-robots.txt", "excluded by the module filter"},
	{"drop/nested/Deep.class", "excluded by the glob"},
	{"keep/drop/Kept.class", "kept: the glob is anchored"},
	{"module-info.class", "excluded by the module filter"},
}

// kotlinShapedEntries materializes the entries a Kotlin module-filter preparation writes today: one file per selected
// entry in central-directory order, with META-INF/listOfEntities.txt selected unconditionally.
func kotlinShapedEntries(t *testing.T, archive, prepared string, selected func(string) bool) []PreparedEntry {
	t.Helper()
	reader, err := zip.OpenReader(archive)
	if err != nil {
		t.Fatal(err)
	}
	defer reader.Close()
	var entries []PreparedEntry
	for _, file := range reader.File {
		if file.Name != "META-INF/listOfEntities.txt" && !selected(file.Name) {
			continue
		}
		input, err := file.Open()
		if err != nil {
			t.Fatal(err)
		}
		data, err := io.ReadAll(input)
		input.Close()
		if err != nil {
			t.Fatal(err)
		}
		name := fmt.Sprintf("entries/%d", len(entries))
		writeTestFile(t, filepath.Join(prepared, name), data)
		entries = append(entries, PreparedEntry{Kind: "file", Name: file.Name, Input: &Reference{Artifact: "prepared", Path: name}})
	}
	return entries
}

func TestModuleExcludesSelectEntriesInCentralDirectoryOrder(t *testing.T) {
	tests := []struct {
		name          string
		manifest      string
		mergeEntities bool
		excludes      []string
		selected      func(string) bool
		want          []string
	}{
		{"keep", "keep", true, []string{"drop/**"},
			func(name string) bool {
				return !strings.HasPrefix(name, "drop/") && jarpack.ModuleOutputNameFilter(name)
			},
			[]string{"keep/Service.class", "META-INF/MANIFEST.MF", "keep/drop/Kept.class", "META-INF/listOfEntities.txt", "__index__"}},
		{"drop", "drop", true, []string{"drop/**"},
			func(name string) bool {
				return !strings.HasPrefix(name, "drop/") && jarpack.ModuleOutputNameFilter(name)
			},
			[]string{"keep/Service.class", "keep/drop/Kept.class", "META-INF/listOfEntities.txt", "__index__"}},
		{"entities survive an exclude", "keep", false, []string{"drop/**", "META-INF/**"},
			func(name string) bool {
				return !strings.HasPrefix(name, "drop/") && !strings.HasPrefix(name, "META-INF/") && jarpack.ModuleOutputNameFilter(name)
			},
			[]string{"keep/Service.class", "META-INF/listOfEntities.txt", "keep/drop/Kept.class", "__index__"}},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			root := t.TempDir()
			recipe, catalogue := samplePlan(root)
			archiveFile(t, filepath.Join(root, "module.jar"), moduleExcludesFixture...)
			recipe.Operations[0].Options.MergeEntities = test.mergeEntities
			recipe.Operations[0].Sources = []Source{{Kind: "archive", Input: &Reference{Artifact: "module"}, Filter: "module", Manifest: test.manifest, Excludes: test.excludes}}
			output, _ := writeExecution(t, recipe, catalogue)
			names, entries := readArchive(t, filepath.Join(output, "lib/plugin.jar"))
			if !slices.Equal(names, test.want) {
				t.Fatalf("selected entries:\n%v\nwant %v", names, test.want)
			}
			if entries["keep/drop/Kept.class"] != "kept: the glob is anchored" {
				t.Fatalf("changed entry bytes: %v", entries)
			}

			prepared := filepath.Join(root, "prepared")
			twin, twinCatalogue := samplePlan(root)
			twinCatalogue.Artifacts = []Artifact{{ID: "prepared", Kind: "directory", Root: prepared}}
			twin.Operations[0].Options.MergeEntities = test.mergeEntities
			twin.Operations[0].Sources = []Source{{Kind: "entries", Manifest: test.manifest,
				Entries: kotlinShapedEntries(t, filepath.Join(root, "module.jar"), prepared, test.selected)}}
			twinOutput, _ := writeExecution(t, twin, twinCatalogue)
			if !bytes.Equal(readTestFile(t, filepath.Join(output, "lib/plugin.jar")), readTestFile(t, filepath.Join(twinOutput, "lib/plugin.jar"))) {
				t.Fatal("the excludes jar differs from the prepared-entries jar")
			}
		})
	}
}

// TestModuleFilterAgreesWithCommonModuleExcludes pins that jarpack.ModuleOutputNameFilter is commonModuleExcludes.
// A module-filter source composes both, so the two statements of the common excludes must agree on every name.
func TestModuleFilterAgreesWithCommonModuleExcludes(t *testing.T) {
	var common []javaglob.Matcher
	for _, pattern := range []string{"**/icon-robots.txt", "icon-robots.txt", ".unmodified", ".hash", "classpath.index", "module-info.class"} {
		matcher, err := javaglob.Compile(pattern)
		if err != nil {
			t.Fatal(err)
		}
		common = append(common, matcher)
	}
	for _, name := range []string{
		"icon-robots.txt", "icons/icon-robots.txt", "a/b/icon-robots.txt", "xicon-robots.txt", "icon-robots.txt.bak",
		".unmodified", "a/.unmodified", ".hash", "a/.hash", "classpath.index", "a/classpath.index",
		"module-info.class", "META-INF/versions/9/module-info.class", "META-INF/MANIFEST.MF", "META-INF/listOfEntities.txt",
		"com/example/Service.class", "standardDsls/a.gdsl", "js/index.js",
	} {
		excluded := slices.ContainsFunc(common, func(matcher javaglob.Matcher) bool { return matcher.Match(name) })
		if jarpack.ModuleOutputNameFilter(name) != !excluded {
			t.Errorf("%q: ModuleOutputNameFilter %v, commonModuleExcludes %v", name, jarpack.ModuleOutputNameFilter(name), excluded)
		}
	}
}
