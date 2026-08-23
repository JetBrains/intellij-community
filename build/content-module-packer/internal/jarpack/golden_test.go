// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

// SHA-256 of the packed jar each recipe in merge_test.go produces. See the comment there for why these are frozen
// rather than derived, and what a change in one means.
const (
	goldenModuleOnly           = "541f94c6720a58b3abc309099382a4d157cf3e485321c3003fb9be078e7d27cf"
	goldenKeepManifest         = "622c52ad7098cee4a36c94665b8759f456f1d7cede38ce6872d28007536227eb"
	goldenLibraryAndModule     = "a09cee3fbb62c83656aef9122a7a6ec00f477ca4a09ff38781a722e395e35114"
	goldenFirstSourceWins      = "8a6a3781636d843471369ce70aaf4101c0da2a154b182375a4551543651c4a9e"
	goldenRewriteBootClassPath = "3cd4c23c2d5293cdff4e8a618f5c3c2f359c50151469c99888fa2685ad86cee9"
	goldenAsymmetricExtra      = "a8bd1e24180de52a9dc83e14b8951e68f86769999976b181119e5f6d91bc36e8"
)
