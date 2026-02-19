#!/usr/bin/env node

/**
 * Generic script to replace library references with module references in IntelliJ project IML files.
 *
 * Usage:
 *   node replace-lib-with-module.js --old-lib=LibName --new-module=intellij.libraries.libname [options]
 *
 * Options:
 *   --old-lib=NAME     Library name to replace (required)
 *   --new-module=NAME  Target module name (required)
 *   --dry-run          Show what would be changed without modifying files
 *   --exclude=DIR      Additional directory to exclude (can be used multiple times, default: fleet, toolbox)
 *   --help             Show this help message
 *
 * Examples:
 *   # Dry run for Guava
 *   node replace-lib-with-module.js --old-lib=Guava --new-module=intellij.libraries.guava --dry-run
 *
 *   # Apply Guava replacement
 *   node replace-lib-with-module.js --old-lib=Guava --new-module=intellij.libraries.guava
 *
 *   # Replace another library with custom exclusions
 *   node replace-lib-with-module.js --old-lib=Jackson --new-module=intellij.libraries.jackson --exclude=custom-dir
 */

const fs = require('fs');
const path = require('path');

// Parse command line arguments
const args = process.argv.slice(2);
const config = {
  dryRun: args.includes('--dry-run'),
  oldLib: null,
  newModule: null,
  excludeDirs: ['fleet', 'toolbox'],
  showHelp: args.includes('--help')
};

// Parse custom arguments
for (const arg of args) {
  if (arg.startsWith('--old-lib=')) {
    config.oldLib = arg.split('=')[1];
  } else if (arg.startsWith('--new-module=')) {
    config.newModule = arg.split('=')[1];
  } else if (arg.startsWith('--exclude=')) {
    const dir = arg.split('=')[1];
    if (!config.excludeDirs.includes(dir)) {
      config.excludeDirs.push(dir);
    }
  }
}

if (config.showHelp) {
  console.log(`
Generic script to replace library references with module references in IntelliJ project IML files.

Usage:
  node replace-lib-with-module.js --old-lib=LibName --new-module=intellij.libraries.libname [options]

Options:
  --old-lib=NAME     Library name to replace (required)
  --new-module=NAME  Target module name (required)
  --dry-run          Show what would be changed without modifying files
  --exclude=DIR      Additional directory to exclude (can be used multiple times, default: fleet, toolbox)
  --help             Show this help message

Examples:
  # Dry run for Guava
  node replace-lib-with-module.js --old-lib=Guava --new-module=intellij.libraries.guava --dry-run

  # Apply Guava replacement
  node replace-lib-with-module.js --old-lib=Guava --new-module=intellij.libraries.guava

  # Replace another library with custom exclusions
  node replace-lib-with-module.js --old-lib=Jackson --new-module=intellij.libraries.jackson --exclude=custom-dir
`);
  process.exit(0);
}

// Validate required arguments
if (!config.oldLib || !config.newModule) {
  console.error('Error: Both --old-lib and --new-module are required arguments.\n');
  console.log('Usage: node replace-lib-with-module.js --old-lib=LibName --new-module=intellij.libraries.libname [options]');
  console.log('Run with --help for more information.');
  process.exit(1);
}

const PROJECT_ROOT = path.join(__dirname, '..', '..');
const MODULES_XML = path.join(PROJECT_ROOT, '.idea', 'modules.xml');

console.log('Configuration:');
console.log(`  Old library: ${config.oldLib}`);
console.log(`  New module: ${config.newModule}`);
console.log(`  Excluded dirs: ${config.excludeDirs.join(', ')}`);
console.log(`  Dry run: ${config.dryRun}`);
console.log('');

// Statistics
const stats = {
  filesScanned: 0,
  filesModified: 0,
  filesSkipped: 0,
  replacements: {
    regular: 0,
    testScope: 0,
    testScopeExported: 0,
    providedScope: 0,
    runtimeScope: 0
  }
};

/**
 * Extract IML file paths from modules.xml
 */
function extractImlPaths(modulesXmlPath) {
  const content = fs.readFileSync(modulesXmlPath, 'utf-8');
  const filepathRegex = /filepath="\$PROJECT_DIR\$\/([^"]+)"/g;
  const paths = [];
  let match;

  while ((match = filepathRegex.exec(content)) !== null) {
    paths.push(match[1]);
  }

  return paths;
}

/**
 * Check if path should be excluded
 */
function shouldExclude(filePath) {
  for (const excludeDir of config.excludeDirs) {
    if (filePath.startsWith(excludeDir + '/') || filePath.includes('/' + excludeDir + '/')) {
      return true;
    }
  }
  return false;
}

/**
 * Create replacement patterns based on library and module names
 */
function createReplacementPatterns(oldLib, newModule) {
  return [
    {
      name: 'regular',
      // <orderEntry type="library" name="Guava" level="project" />
      pattern: new RegExp(
        `<orderEntry type="library" name="${oldLib}" level="project" />`,
        'g'
      ),
      replacement: `<orderEntry type="module" module-name="${newModule}" />`
    },
    {
      name: 'testScope',
      // <orderEntry type="library" scope="TEST" name="Guava" level="project" />
      pattern: new RegExp(
        `<orderEntry type="library" scope="TEST" name="${oldLib}" level="project" />`,
        'g'
      ),
      replacement: `<orderEntry type="module" module-name="${newModule}" scope="TEST" />`
    },
    {
      name: 'testScopeExported',
      // <orderEntry type="library" exported="" scope="TEST" name="Guava" level="project" />
      pattern: new RegExp(
        `<orderEntry type="library" exported="" scope="TEST" name="${oldLib}" level="project" />`,
        'g'
      ),
      replacement: `<orderEntry type="module" module-name="${newModule}" exported="" scope="TEST" />`
    },
    {
      name: 'providedScope',
      // <orderEntry type="library" scope="PROVIDED" name="Guava" level="project" />
      pattern: new RegExp(
        `<orderEntry type="library" scope="PROVIDED" name="${oldLib}" level="project" />`,
        'g'
      ),
      replacement: `<orderEntry type="module" module-name="${newModule}" scope="PROVIDED" />`
    },
    {
      name: 'runtimeScope',
      // <orderEntry type="library" scope="RUNTIME" name="Guava" level="project" />
      pattern: new RegExp(
        `<orderEntry type="library" scope="RUNTIME" name="${oldLib}" level="project" />`,
        'g'
      ),
      replacement: `<orderEntry type="module" module-name="${newModule}" scope="RUNTIME" />`
    }
  ];
}

/**
 * Process a single IML file
 */
function processImlFile(filePath) {
  const absolutePath = path.join(PROJECT_ROOT, filePath);

  if (!fs.existsSync(absolutePath)) {
    console.log(`  ⚠ File not found: ${filePath}`);
    stats.filesSkipped++;
    return;
  }

  let content = fs.readFileSync(absolutePath, 'utf-8');
  let modified = false;
  const fileReplacements = {};

  const patterns = createReplacementPatterns(config.oldLib, config.newModule);

  for (const { name, pattern, replacement } of patterns) {
    const matches = content.match(pattern);
    if (matches && matches.length > 0) {
      fileReplacements[name] = matches.length;
      stats.replacements[name] += matches.length;
      content = content.replace(pattern, replacement);
      modified = true;
    }
  }

  stats.filesScanned++;

  if (modified) {
    stats.filesModified++;

    const replacementSummary = Object.entries(fileReplacements)
      .map(([type, count]) => `${count} ${type}`)
      .join(', ');

    console.log(`  ✓ ${filePath} (${replacementSummary})`);

    if (!config.dryRun) {
      fs.writeFileSync(absolutePath, content, 'utf-8');
    }
  }
}

/**
 * Main execution
 */
function main() {
  console.log('Reading modules from .idea/modules.xml...\n');

  const imlPaths = extractImlPaths(MODULES_XML);
  console.log(`Found ${imlPaths.length} module files in modules.xml\n`);

  console.log('Processing IML files:\n');

  for (const imlPath of imlPaths) {
    if (shouldExclude(imlPath)) {
      stats.filesSkipped++;
      continue;
    }

    processImlFile(imlPath);
  }

  // Print summary
  console.log('\n' + '='.repeat(60));
  console.log('Summary:');
  console.log('='.repeat(60));
  console.log(`Files scanned: ${stats.filesScanned}`);
  console.log(`Files modified: ${stats.filesModified}`);
  console.log(`Files skipped: ${stats.filesSkipped}`);
  console.log('');
  console.log('Replacements by type:');
  console.log(`  Regular scope: ${stats.replacements.regular}`);
  console.log(`  TEST scope: ${stats.replacements.testScope}`);
  console.log(`  TEST scope (exported): ${stats.replacements.testScopeExported}`);
  console.log(`  PROVIDED scope: ${stats.replacements.providedScope}`);
  console.log(`  RUNTIME scope: ${stats.replacements.runtimeScope}`);
  console.log(`  Total: ${stats.replacements.regular + stats.replacements.testScope + stats.replacements.testScopeExported + stats.replacements.providedScope + stats.replacements.runtimeScope}`);

  if (config.dryRun) {
    console.log('\n⚠ DRY RUN MODE - No files were modified');
    console.log('Run without --dry-run to apply changes');
  } else {
    console.log('\n✓ All changes applied successfully');
  }
}

// Run the script
try {
  main();
} catch (error) {
  console.error('Error:', error.message);
  process.exit(1);
}
